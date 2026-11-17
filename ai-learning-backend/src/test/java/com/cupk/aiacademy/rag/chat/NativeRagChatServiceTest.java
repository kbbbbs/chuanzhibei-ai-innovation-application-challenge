package com.cupk.aiacademy.rag.chat;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.cupk.aiacademy.rag.RagSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2 conversation persistence with controlled model output, not a model-quality benchmark. */
class NativeRagChatServiceTest {
    NativeRagService knowledge;
    OpenAiChatModel model;
    NativeRagChatService chat;
    JdbcTemplate db;
    final String base = "7ecbd8ec-c48c-472d-8cae-16c4a8382678";
    final RagSearchHit source = new RagSearchHit("chunk-1", "doc-1", base, "教学资料.md",
            "梯度下降沿负梯度方向更新参数。", null, 1, 2.3, "[1]");

    @BeforeEach void setup() {
        knowledge = mock(NativeRagService.class);
        model = mock(OpenAiChatModel.class);
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        chat = new NativeRagChatService(knowledge, db, new ObjectMapper(), model, 5);
        chat.initialize();
        login(2);
        when(knowledge.searchForOwner(anyList(), anyString(), eq(2), anyInt()))
                .thenReturn(Result.success(new RagSearchResponse(List.of(source), "bm25", null)));
    }

    @AfterEach void cleanup() { chat.shutdown(); UserContext.clear(); }

    @Test void actualRetrievalMetadataAndSourceVersionsSurvivePromptSelectionAndHistory() {
        var hit=new com.cupk.aiacademy.rag.RagSearchHit(source.chunkId(),source.documentId(),source.kbId(),source.title(),source.content(),source.page(),source.ordinal(),source.score(),"[1]",List.of(),"index-7",3,2);
        var snapshot=new com.cupk.aiacademy.rag.RagIndexSnapshot(hit.documentId(),"index-7",3,2,"qwen-test",2,true,"chunk-v3");
        when(knowledge.searchForOwner(anyList(),anyString(),eq(2),anyInt())).thenReturn(Result.success(new RagSearchResponse(List.of(hit),"hybrid","partial","HYBRID","PARTIAL_VECTOR_COVERAGE",List.of(snapshot))));
        when(model.call(any(Prompt.class))).thenReturn(response("参数更新来自资料。[1]"));
        var result=chat.chat(new RagChatRequest(List.of(base),"如何更新？",null));
        assertEquals("HYBRID",result.actualMode());assertEquals("PARTIAL_VECTOR_COVERAGE",result.degradedReason());
        assertEquals("index-7",result.citations().getFirst().indexVersion());assertEquals(List.of(snapshot),result.indexVersions());
        var history=chat.messages(result.sessionId());assertEquals("HYBRID",history.getLast().get("actualMode"));
        assertEquals(List.of(snapshot),history.getLast().get("indexVersions"));
    }

    @Test void longFormulaSurvivesTheAnswerContextBudgetIntact() {
        String formula="$$\\begin{aligned}"+"x_i &= a_i + b_i \\\\ ".repeat(100)+"\\end{aligned}$$";
        var hit=new RagSearchHit("math", "doc",base,"公式.pdf","参数定义\n"+formula+"\n变量说明",3,1,1,"[1]");
        when(knowledge.searchForOwner(anyList(),anyString(),eq(2),anyInt()))
                .thenReturn(Result.success(new RagSearchResponse(List.of(hit),"bm25",null)));
        when(model.call(any(Prompt.class))).thenReturn(response("公式来自资料。[1]"));
        chat.chat(new RagChatRequest(List.of(base),"公式是什么？",null));
        var prompt=ArgumentCaptor.forClass(Prompt.class);verify(model).call(prompt.capture());
        assertTrue(prompt.getValue().getContents().contains(formula));
    }

    @Test void formulaArrayIndicesAreNotMistakenForCitationNumbers() {
        assertEquals("ANSWERED",GroundedAnswer.validate("公式 $a[99]=b$。[1]",List.of(source)).status());
        assertEquals("CITATION_CHECK_FAILED",GroundedAnswer.validate("只有公式 $a[1]=b$",List.of(source)).status());
    }

    @Test void citationFailureExcerptDoesNotCutFormula() {
        String formula="$$"+"x+".repeat(400)+"y$$";
        var hit=new RagSearchHit("math","doc",base,"公式.pdf","Definition\n"+formula,1,1,1,"[1]");
        String excerpt=GroundedAnswer.validate("No citations",List.of(hit)).answer();
        assertTrue(!excerpt.contains("$$") || excerpt.contains(formula));
    }

    @Test void actualPromptSourcesArePersistedWithStableHistoryOrder() {
        when(model.call(any(Prompt.class))).thenReturn(response("参数沿负梯度更新。[1]"));
        RagChatResult result = chat.chat(new RagChatRequest(List.of(base), "梯度下降如何更新？", null));
        assertEquals("ANSWERED", result.status());
        assertEquals(List.of(source), result.citations());
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertTrue(prompt.getValue().getContents().contains(source.content()));
        assertFalse(prompt.getValue().getContents().contains("第 null 页"));
        var history = chat.messages(result.sessionId());
        assertEquals(List.of("user", "assistant"), history.stream().map(m -> m.get("role")).toList());
        assertEquals(List.of(source), history.get(1).get("citations"));
        assertThrows(BusinessException.class, () -> chat.chat(new RagChatRequest(List.of(), "问题", result.sessionId())));
        login(3);
        assertThrows(ForbiddenException.class, () -> chat.messages(result.sessionId()));
        assertTrue(chat.listSessions().isEmpty());
    }

    @Test void unmatchedQuestionDoesNotCallModelOrInventCitations() {
        when(knowledge.searchForOwner(anyList(), anyString(), eq(2), anyInt()))
                .thenReturn(Result.success(new RagSearchResponse(List.of(), "bm25", null)));
        var result = chat.chat(new RagChatRequest(List.of(base), "无资料支持的问题", null));
        assertEquals("NO_EVIDENCE", result.status());
        assertTrue(result.citations().isEmpty());
        verifyNoInteractions(model);
    }

    @Test void legacyKnowledgeRequestAcceptsAnEmptyOptionalSelection() {
        when(model.call(any(Prompt.class))).thenReturn(response("参数更新。[1]"));
        var result=chat.chat(new RagChatRequest(List.of(base),"梯度下降",null,null,null,""));
        assertEquals("ANSWERED",result.status());
    }

    @Test void failedRetrievalDoesNotBecomeAnUngroundedAnswer() {
        when(knowledge.searchForOwner(anyList(), anyString(), eq(2), anyInt())).thenReturn(Result.error("403", "denied"));
        assertThrows(BusinessException.class, () -> chat.chat(new RagChatRequest(List.of(base), "问题", null)));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM t_native_rag_message", Integer.class));
        verifyNoInteractions(model);
    }

    @Test void invalidCitationIsReplacedByTraceableExcerptsAndPersistsThatResult() {
        when(model.call(any(Prompt.class))).thenReturn(response("来自不存在的文献。[999]"));
        var result = chat.chat(new RagChatRequest(List.of(base), "梯度下降", null));
        assertEquals("CITATION_CHECK_FAILED", result.status());
        assertTrue(result.answer().contains(source.content()));
        assertFalse(result.answer().contains("不存在的文献"));
        assertEquals(result.answer(), chat.messages(result.sessionId()).get(1).get("content"));
    }

    @Test void sharingRevokedDuringModelCallBlocksStoredAnswerAndLaterHistory() {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            doThrow(new ForbiddenException("资料已撤回")).when(knowledge).requireReadableBaseIdsForOwner(anyList(), eq(2));
            return response("梯度更新。[1]");
        });
        String session = chat.createSession(base, "撤回测试").get("id").toString();
        assertThrows(ForbiddenException.class, () -> chat.chat(new RagChatRequest(List.of(base), "梯度", session)));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM t_native_rag_message WHERE role='assistant'", Integer.class));
        assertThrows(ForbiddenException.class, () -> chat.messages(session));
    }

    @Test void absentCitationAndOutOfRangeReferenceFailButReferencedSubsetKeepsItsNumber() {
        RagSearchHit second = new RagSearchHit("c2", "d2", base, "二.pdf", "内容二", 2, 2, 1, "[2]");
        assertEquals("CITATION_CHECK_FAILED", GroundedAnswer.validate("没有编号", List.of(source)).status());
        assertEquals("CITATION_CHECK_FAILED", GroundedAnswer.validate("编号[0]", List.of(source)).status());
        assertEquals("CITATION_CHECK_FAILED", GroundedAnswer.validate("编号[1]和[10000]", List.of(source)).status());
        assertEquals("CITATION_CHECK_FAILED", GroundedAnswer.validate("编号[1]和[999999999999999999999]", List.of(source)).status());
        var result = GroundedAnswer.validate("答案[2]", List.of(source, second));
        assertEquals(List.of(second), result.citations());
        assertEquals("[2]", result.citations().getFirst().citation());
    }

    private ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
    private void login(int id) {
        var user = new AuthUtils.LoginUser(); user.setUserId(id); user.setRoleCode(id == 2 ? "teacher" : "student");
        UserContext.set(user);
    }
}
