package com.cupk.aiacademy.rag.chat;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CourseRagChatTest {
    @TempDir Path files;
    JdbcTemplate db;
    NativeRagService knowledge;
    CourseRagService courses;
    NativeRagChatService chat;
    OpenAiChatModel model;
    PermissionService permissions;
    String document;

    @BeforeEach void setup() {
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE t_course(id INT PRIMARY KEY,teacher_id INT)");
        db.execute("CREATE TABLE t_chapter(id INT PRIMARY KEY,course_id INT,status INT)");
        db.execute("CREATE TABLE t_lesson(id INT PRIMARY KEY,chapter_id INT,status INT)");
        db.update("INSERT INTO t_course VALUES(10,1),(20,1)");
        db.update("INSERT INTO t_chapter VALUES(100,10,1),(200,20,1)");
        db.update("INSERT INTO t_lesson VALUES(101,100,1),(201,200,1)");
        permissions = mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(call -> actor(call.getArgument(0)));
        when(permissions.canReadCourseMaterials(any(), eq(10))).thenReturn(true);
        var auth = new RagAuthorizationService(permissions, db);
        var json = new ObjectMapper();
        var embedding = new RagEmbeddingClient(false,"","","",json);
        knowledge = new NativeRagService(db,new RagDocumentParser(),embedding,json,auth,
                new RagFileStore(files.toString()),new RagSchemaMigrator(db),
                new com.cupk.aiacademy.rag.repository.RagIndexVersionRepository(db));
        knowledge.initialize();
        courses = new CourseRagService(db,auth,new RagRetrievalService(embedding,json));
        model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("依据课程资料解释。[1]")))));
        chat = new NativeRagChatService(knowledge,db,json,model,5);
        chat.configureCourses(courses);
        chat.initialize();
        login(1);
        String base = data(knowledge.createKnowledgeBase("课程资料","")).get("id").toString();
        document = data(knowledge.uploadFile(base,"梯度下降使用负梯度方向更新参数。".getBytes(java.nio.charset.StandardCharsets.UTF_8),"course.txt")).get("id").toString();
        assertEquals("200",knowledge.grantDocument(document,10,1,true,false).getCode());
        login(2);
    }

    @AfterEach void cleanup() { chat.shutdown(); UserContext.clear(); db.execute("DROP ALL OBJECTS"); }

    @Test void studentCanSearchGrantedDocumentsAndChatWithoutOwningPrivateLibrary() {
        assertEquals(1,courses.documents(10,2).size());
        assertTrue(((List<?>)knowledge.listKnowledgeBases().getData()).isEmpty());
        var result = chat.chat(request("梯度下降",null,"负梯度",10,101));
        assertEquals("course",result.mode());
        assertEquals(document,result.citations().getFirst().documentId());
        assertEquals("负梯度",chat.messages(result.sessionId()).getFirst().get("selectedText"));
        assertEquals(10,chat.listSessions().getFirst().get("courseId"));
    }

    @Test void selectionOnlyAnswerWorksWithNoGrantedDocuments() {
        db.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP");
        var result = chat.chat(request("解释这段",null,"梯度下降沿负梯度更新",10,101));
        assertEquals("selection",result.mode());
        assertTrue(result.citations().isEmpty());
        assertTrue(result.warning().contains("仅依据选中文本"));
    }

    @Test void noSourcesAndNoSelectionReturnsNoEvidenceWithoutModelCall() {
        db.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP");
        var result = chat.chat(request("解释",null,"",10,101));
        assertEquals("NO_EVIDENCE",result.status());
        verifyNoInteractions(model);
    }

    @Test void followupPromptIncludesPreviousSelectionFromPersistedHistory() {
        var first=chat.chat(request("解释这段",null,"历史选文特别标记",10,101));
        chat.chat(request("梯度下降再解释",first.sessionId(),"",10,101));
        var prompt=org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model,times(2)).call(prompt.capture());
        assertTrue(prompt.getAllValues().getLast().getContents().contains("历史选文特别标记"));
    }

    @Test void courseAndLessonSpoofingIsRejected() {
        assertThrows(RagAccessException.class,()->chat.chat(request("梯度",null,"选文",20,201)));
        assertThrows(RagAccessException.class,()->chat.chat(request("梯度",null,"选文",10,201)));
        db.update("UPDATE t_chapter SET status=0 WHERE id=100");
        assertThrows(RagAccessException.class,()->chat.chat(request("梯度",null,"选文",10,101)));
    }

    @Test void revokedGrantBlocksExistingHistoryAndFurtherRequests() {
        var result = chat.chat(request("梯度",null,"",10,101));
        db.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP");
        assertThrows(RagAccessException.class,()->chat.messages(result.sessionId()));
        assertThrows(RagAccessException.class,()->chat.chat(request("继续",result.sessionId(),"选文",10,101)));
        assertTrue(chat.listSessions().isEmpty());
        chat.deleteSession(result.sessionId());
    }

    @Test void revokeDuringModelCallPreventsSavingAnswer() {
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            db.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("答案[1]"))));
        });
        assertThrows(RagAccessException.class,()->chat.chat(request("梯度",null,"",10,101)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM t_native_rag_message WHERE role='assistant'",Integer.class));
    }

    @Test void otherUserAndOtherLessonCannotReuseSession() {
        var result = chat.chat(request("梯度",null,"",10,101));
        login(3);
        assertThrows(RuntimeException.class,()->chat.messages(result.sessionId()));
        login(2);
        assertThrows(RuntimeException.class,()->chat.chat(request("梯度",result.sessionId(),"",10,null)));
    }

    @Test void migrationPreservesExistingConversationAndIsRepeatable() {
        chat.initialize();
        var result = chat.chat(request("梯度",null,"",10,101));
        chat.initialize();
        assertEquals(2,chat.messages(result.sessionId()).size());
    }

    @Test void grantStaysPinnedToOldVersionAfterTeacherReplacesDocument() {
        login(1);
        assertEquals("200",knowledge.replaceDocument(document,
                "新版本只讲支持向量机。".getBytes(java.nio.charset.StandardCharsets.UTF_8),"new.txt").getCode());
        login(2);
        var result=chat.chat(request("梯度下降",null,"",10,101));
        assertEquals(1,result.citations().getFirst().documentVersion());
        assertTrue(result.citations().getFirst().content().contains("负梯度"));
        assertFalse(result.citations().getFirst().content().contains("支持向量机"));
    }

    @Test void downloadPermissionDoesNotAuthorizeReadingOrExpandOtherDocuments() {
        db.update("UPDATE native_rag_course_grant SET read_excerpt=FALSE,download_allowed=TRUE");
        assertTrue(courses.documents(10,2).isEmpty());
        var result=chat.chat(request("梯度",null,"",10,101));
        assertEquals("NO_EVIDENCE",result.status());
        verifyNoInteractions(model);
    }

    @Test void courseRetrievalFailureDoesNotFallBackToSelectionExplanation() {
        var broken=mock(CourseRagService.class);
        when(broken.scope(eq(10),eq(101),eq(2))).thenReturn(List.of());
        when(broken.search(eq(10),eq(101),eq(2),anyString(),anyList()))
                .thenThrow(new IllegalStateException("controlled retrieval failure"));
        chat.configureCourses(broken);
        assertThrows(IllegalStateException.class,()->chat.chat(request("梯度",null,"选文",10,101)));
        verifyNoInteractions(model);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM t_native_rag_message",Integer.class));
    }

    RagChatRequest request(String query,String session,String selection,Integer course,Integer lesson) {
        return new RagChatRequest(List.of(),query,session,course,lesson,selection);
    }
    AuthUtils.LoginUser actor(int id) {
        var actor = new AuthUtils.LoginUser();actor.setUserId(id);actor.setRoleCode(id==1?"teacher":"student");return actor;
    }
    void login(int id) { UserContext.set(actor(id)); }
    @SuppressWarnings("unchecked") Map<String,Object> data(com.cupk.aiacademy.common.Result r) {
        assertEquals("200",r.getCode(),r.getMsg());return (Map<String,Object>)r.getData();
    }
}
