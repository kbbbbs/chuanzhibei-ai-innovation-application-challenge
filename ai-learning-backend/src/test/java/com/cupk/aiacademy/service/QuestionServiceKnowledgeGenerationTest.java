package com.cupk.aiacademy.service;

import com.cupk.aiacademy.agent.core.service.AgentChatService;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.GenerateQuestionsFromKbRequest;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.mapper.AgentDefinitionMapper;
import com.cupk.aiacademy.mapper.ExamAnswerMapper;
import com.cupk.aiacademy.mapper.ExamPaperQuestionMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.preparation.PreparationModelGateway;
import com.cupk.aiacademy.pojo.AgentDefinition;
import com.cupk.aiacademy.rag.NativeRagService;
import com.cupk.aiacademy.rag.RagSearchHit;
import com.cupk.aiacademy.rag.RagSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionServiceKnowledgeGenerationTest {
    private final NativeRagService rag = mock(NativeRagService.class);
    private final PreparationModelGateway modelGateway = mock(PreparationModelGateway.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final AgentDefinitionMapper definitions = mock(AgentDefinitionMapper.class);
    private final OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
    private final QuestionService service = new QuestionService(
            mock(QuestionMapper.class), new ObjectMapper(), rag, permissions,
            mock(AgentChatService.class), definitions,
            mock(ExamPaperQuestionMapper.class), mock(ExamAnswerMapper.class), chatModel, modelGateway);

    @Test
    void usesNativeSourceChunksAndPersistsActualGenerationReferences() {
        login(7);
        RagSearchHit source = source("doc-1", "chunk-1", "图灵测试用于判断机器能否在行为上表现出智能。", 3);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.success(new RagSearchResponse(List.of(source), "bm25", "使用关键词检索")));
        when(modelGateway.generateQuestions(anyString(), anyString())).thenReturn(List.of(question()));

        List<QuestionRequestDTO> result = service.generateFromKnowledgeBase(request(), 7L);

        assertEquals(1, result.size());
        assertEquals(12L, result.getFirst().getCourseId());
        assertEquals(7L, result.getFirst().getTeacherId());
        assertTrue(result.getFirst().getAnalysis().contains("生成参考资料（供教师核验）"));
        assertTrue(result.getFirst().getAnalysis().contains("文档ID：doc-1；片段ID：chunk-1；第3页"));
        assertTrue(result.getFirst().getAnalysis().contains("不代表对每道题的逐项事实核验"));
        verify(rag, times(2)).requireReadableBaseIdsForOwner(List.of("kb-1"), 7);
        verify(permissions).requireCourseTeacher(12);
        verify(modelGateway).generateQuestions(contains("图灵测试用于判断机器"), contains("严格生成3道题目"));
    }

    @Test
    void emptyTopicSearchUsesExplicitAuthorizedDocumentOverview() {
        login(7);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.success(new RagSearchResponse(List.of(), "bm25", "")));
        when(rag.listKnowledgeForOwner("kb-1", 7, 200, 0)).thenReturn(Result.success(List.of(
                Map.of("id", "doc-1", "parseStatus", "success"))));
        when(rag.contentForOwner("doc-1", 7)).thenReturn(Result.success(Map.of("chunks", List.of(
                source("doc-1", "chunk-1", "机器学习包含监督学习和无监督学习。", 2)))));
        when(modelGateway.generateQuestions(anyString(), anyString())).thenReturn(List.of(question()));

        List<QuestionRequestDTO> result = service.generateFromKnowledgeBase(request(), 7L);

        assertTrue(result.getFirst().getAnalysis().contains("这些片段不是问题相关性命中"));
        verify(modelGateway).generateQuestions(contains("文档ID：doc-1；片段ID：chunk-1；第2页"), anyString());
    }

    @Test
    void retrievalFailureCannotBecomeModelOnlyGeneration() {
        login(7);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10)))
                .thenReturn(Result.error("503", "索引暂不可用"));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.generateFromKnowledgeBase(request(), 7L));

        assertTrue(failure.getMessage().contains("索引暂不可用"));
        verifyNoInteractions(modelGateway);
        verify(rag, never()).listKnowledgeForOwner(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void invalidOrPrivateForeignBaseCannotReachTheModel() {
        login(7);
        doThrow(new BusinessException("无权访问知识库"))
                .when(rag).requireReadableBaseIdsForOwner(List.of("kb-1"), 7);

        assertThrows(BusinessException.class, () -> service.generateFromKnowledgeBase(request(), 7L));

        verify(rag, never()).searchForOwner(anyList(), anyString(), anyInt(), anyInt());
        verifyNoInteractions(modelGateway);
    }

    @Test
    void pdfExtractionReadsOnlyItsOwnUploadedDocumentWithoutAnExternalSession() {
        // The administrator may generate for teacher 7; access still follows
        // the acting administrator 9 rather than the question author.
        login(9);
        when(rag.listKnowledgeBases("mine")).thenReturn(Result.success(List.of()));
        when(rag.createKnowledgeBase(eq("EduLoop题库-9"), anyString()))
                .thenReturn(Result.success(Map.of("id", "kb-private")));
        when(rag.uploadFile(eq("kb-private"), any(byte[].class), eq("题目.pdf")))
                .thenReturn(Result.success(Map.of("id", "uploaded-doc", "parseStatus", "success")));
        when(rag.contentForOwner("uploaded-doc", 9)).thenReturn(Result.success(Map.of("chunks", List.of(
                source("uploaded-doc", "uploaded-chunk", "题目：图灵测试关注什么？答案：机器的智能行为。", 1)))));
        when(modelGateway.generateQuestions(anyString(), anyString())).thenReturn(List.of(question()));

        var file = new MockMultipartFile("file", "题目.pdf", "application/pdf", new byte[] {1, 2, 3});
        List<QuestionRequestDTO> result = service.extractQuestions(file, 12L, 7L);

        assertEquals(7L, result.getFirst().getTeacherId());
        assertTrue(result.getFirst().getAnalysis().contains("片段ID：uploaded-chunk"));
        verify(rag).contentForOwner("uploaded-doc", 9);
        verify(rag, never()).searchForOwner(anyList(), anyString(), anyInt(), anyInt());
        verify(modelGateway).generateQuestions(contains("仅提取原文已有题目"), contains("文档ID：uploaded-doc"));
    }

    @Test
    void oversizedExtractionFailsExplicitlyBeforeCallingTheModel() {
        login(7);
        when(rag.listKnowledgeBases("mine")).thenReturn(Result.success(List.of(
                Map.of("id", "kb-private", "name", "EduLoop题库-7", "owner_id", 7))));
        when(rag.uploadFile(anyString(), any(byte[].class), anyString()))
                .thenReturn(Result.success(Map.of("id", "doc-big")));
        when(rag.contentForOwner("doc-big", 7)).thenReturn(Result.success(Map.of("chunks", List.of(
                source("doc-big", "chunk-big", "题".repeat(13000), 1)))));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.extractQuestions(
                new MockMultipartFile("file", "题目.pdf", "application/pdf", new byte[] {1}), 12L, 7L));

        assertTrue(failure.getMessage().contains("拆分文档"));
        verifyNoInteractions(modelGateway);
    }

    @Test
    void sharingRevokedDuringGenerationPreventsReturningTheGeneratedPrivateDraft() {
        login(7);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10))).thenReturn(Result.success(
                new RagSearchResponse(List.of(source("doc-1", "chunk-1", "共享课程的真实内容", 1)), "bm25", "")));
        when(modelGateway.generateQuestions(anyString(), anyString())).thenAnswer(call -> {
            doThrow(new BusinessException("资料共享已撤销")).when(rag).requireReadableBaseIdsForOwner(List.of("kb-1"), 7);
            return List.of(question());
        });

        BusinessException failure = assertThrows(BusinessException.class, () -> service.generateFromKnowledgeBase(request(), 7L));

        assertTrue(failure.getMessage().contains("资料共享已撤销"));
        verify(modelGateway).generateQuestions(anyString(), anyString());
    }

    @Test
    void sharedGatewayErrorsDoNotExposeProviderResponses() {
        login(7);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10))).thenReturn(Result.success(
                new RagSearchResponse(List.of(source("doc-1", "chunk-1", "课程内容", 1)), "bm25", "")));
        when(modelGateway.generateQuestions(anyString(), anyString()))
                .thenThrow(new BusinessException("PRIVATE_PROVIDER_RESPONSE"));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.generateFromKnowledgeBase(request(), 7L));

        assertFalse(failure.getMessage().contains("PRIVATE_PROVIDER_RESPONSE"));
        assertTrue(failure.getMessage().contains("检查模型配置"));
    }

    @Test
    void selectedModelAndTemperatureKeepTheirRouteButProviderErrorsAreSanitized() {
        login(7);
        AgentDefinition agent = new AgentDefinition();
        agent.setModel("selected-model");
        agent.setTemperature(new java.math.BigDecimal("0.2"));
        when(definitions.selectById(5)).thenReturn(agent);
        when(rag.searchForOwner(anyList(), anyString(), eq(7), eq(10))).thenReturn(Result.success(
                new RagSearchResponse(List.of(source("doc-1", "chunk-1", "课程内容", 1)), "bm25", "")));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new IllegalStateException("PRIVATE_PROVIDER_RESPONSE"));
        GenerateQuestionsFromKbRequest request = request();
        request.setAgentId(5);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.generateFromKnowledgeBase(request, 7L));

        assertFalse(failure.getMessage().contains("PRIVATE_PROVIDER_RESPONSE"));
        var prompt = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(prompt.capture());
        var options = (org.springframework.ai.openai.OpenAiChatOptions) prompt.getValue().getOptions();
        assertEquals("selected-model", options.getModel());
        assertEquals(Double.valueOf(0.2), options.getTemperature());
        verifyNoInteractions(modelGateway);
    }

    private void login(int actorId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(actorId);
        user.setRoleCode("teacher");
        when(permissions.currentUser()).thenReturn(user);
    }

    private GenerateQuestionsFromKbRequest request() {
        GenerateQuestionsFromKbRequest request = new GenerateQuestionsFromKbRequest();
        request.setKnowledgeBaseId("kb-1");
        request.setCourseId(12L);
        request.setCount(3);
        request.setTypes(List.of("SHORT_ANSWER"));
        request.setDifficulty("MEDIUM");

        return request;
    }

    private RagSearchHit source(String documentId, String chunkId, String content, int page) {
        return new RagSearchHit(chunkId, documentId, "kb-1", "人工智能绪论.pdf", content, page, 0, 1, "[1]");
    }

    private QuestionRequestDTO question() {
        QuestionRequestDTO generated = new QuestionRequestDTO();
        generated.setContent("图灵测试关注什么？");
        generated.setType(QuestionType.SHORT_ANSWER);
        generated.setDifficulty(Difficulty.MEDIUM);
        generated.setAnalysis("理解智能的行为评价。");
        return generated;
    }
}
