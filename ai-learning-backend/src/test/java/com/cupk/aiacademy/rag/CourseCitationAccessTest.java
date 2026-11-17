package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CourseCitationAccessTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    NativeRagService service;
    PermissionService permissions;
    String document, chunk;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        permissions = mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(call -> loginUser(call.getArgument(0)));
        when(permissions.canReadCourseMaterials(any(), eq(10))).thenReturn(true);
        var json = new ObjectMapper();
        service = new NativeRagService(jdbc, new RagDocumentParser(), new RagEmbeddingClient(false,"","","",json),
                json, new RagAuthorizationService(permissions,jdbc), new RagFileStore(temp.toString()), new RagSchemaMigrator(jdbc), new RagIndexVersionRepository(jdbc));
        service.initialize(); UserContext.set(loginUser(1));
        String base = ((Map<?,?>)service.createKnowledgeBase("课程教材", "").getData()).get("id").toString();
        document = ((Map<?,?>)service.uploadFile(base,"Original course source".getBytes(StandardCharsets.UTF_8),"notes.txt").getData()).get("id").toString();
        chunk = jdbc.queryForObject("SELECT id FROM native_rag_chunk WHERE document_id=?",String.class,document);
        jdbc.update("INSERT INTO native_rag_course_grant(id,owner_id,course_id,document_id,document_version,read_excerpt,download_allowed) VALUES(?,1,10,?,1,TRUE,FALSE)",UUID.randomUUID().toString(),document);
        UserContext.set(loginUser(2));
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    private AuthUtils.LoginUser loginUser(Integer id) {
        var user = new AuthUtils.LoginUser(); user.setUserId(id); user.setRoleCode(id == 1 ? "teacher" : "student"); return user;
    }
    private Result read() throws Exception {
        return (Result) NativeRagService.class.getMethod("courseChunk",Integer.class,String.class).invoke(service,10,chunk);
    }

    @Test void aStudentReadsAnAuthorizedCitationWithoutOwnerAccessOrDownloadPermission() throws Exception {
        assertEquals("403",service.chunk(chunk).getCode());
        Result result=read(); assertEquals("200",result.getCode(),result.getMsg());
        RagSearchHit source=(RagSearchHit)result.getData();
        assertEquals(document,source.documentId()); assertEquals(1,source.documentVersion());
        assertEquals("Original course source",source.content());
    }

    @Test void wrongCourseRevokedGrantAndMissingMembershipCannotReadCitation() throws Exception {
        jdbc.update("UPDATE native_rag_course_grant SET course_id=11"); assertEquals("403",read().getCode());
        jdbc.update("UPDATE native_rag_course_grant SET course_id=10,revoked_at=CURRENT_TIMESTAMP"); assertEquals("403",read().getCode());
        jdbc.update("UPDATE native_rag_course_grant SET revoked_at=NULL");
        when(permissions.canReadCourseMaterials(any(),eq(10))).thenReturn(false); assertEquals("403",read().getCode());
    }

    @Test void aGrantForAnotherDocumentVersionCannotReadTheCurrentCitation() throws Exception {
        jdbc.update("UPDATE native_rag_course_grant SET document_version=2");
        assertEquals("403",read().getCode());
    }
}
