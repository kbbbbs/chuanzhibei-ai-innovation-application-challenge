package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.rag.repository.RagIndexVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class K01DocumentLifecycleTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    NativeRagService service;
    RagFileStore files;
    RagEmbeddingClient embedding;
    PermissionService permissions;
    final Map<Integer,String> roles=new HashMap<>();
    final Set<Integer> courseMembers=new HashSet<>();
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        roles.put(1,"teacher");roles.put(2,"teacher");roles.put(3,"student");roles.put(9,"admin");
        courseMembers.add(3);
        permissions=mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(call->{
            int id=call.getArgument(0);if(!roles.containsKey(id))throw new ForbiddenException("disabled");
            var user=new AuthUtils.LoginUser();user.setUserId(id);user.setRoleCode(roles.get(id));return user;
        });
        when(permissions.canReadCourseMaterials(any(),anyInt())).thenAnswer(call->{
            AuthUtils.LoginUser actor=call.getArgument(0);Integer course=call.getArgument(1);
            return course==10 && (actor.getUserId()==1 || courseMembers.contains(actor.getUserId()));
        });
        embedding=mock(RagEmbeddingClient.class);
        when(embedding.fingerprint()).thenReturn("test-fingerprint");
        when(embedding.modelName()).thenReturn("synthetic-test-model");
        files=spy(new RagFileStore(temp.resolve("private").toString()));
        service=new NativeRagService(jdbc,new RagDocumentParser(),embedding,new ObjectMapper(),
                new RagAuthorizationService(permissions,jdbc),files,new RagSchemaMigrator(jdbc),new RagIndexVersionRepository(jdbc));
        service.initialize();
        jdbc.execute("CREATE TABLE t_course(id INT PRIMARY KEY,teacher_id INT,status INT)");
        jdbc.update("INSERT INTO t_course VALUES(10,1,1),(20,2,1)");
        login(1);
    }
    @AfterEach void cleanup(){UserContext.clear();jdbc.execute("DROP ALL OBJECTS");}
    void login(int id){var user=new AuthUtils.LoginUser();user.setUserId(id);user.setRoleCode(roles.get(id));UserContext.set(user);}
    @SuppressWarnings("unchecked") Map<String,Object> data(Result value){assertEquals("200",value.getCode(),value.getMsg());return (Map<String,Object>)value.getData();}
    String base(){return data(service.createKnowledgeBase("Teacher materials","")).get("id").toString();}
    byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    String upload(String base,String text){return data(service.uploadFile(base,bytes(text),"lecture.txt")).get("id").toString();}

    @Test void grantsAreVersionSpecificAndDownloadPermissionIsIndependent() throws Exception {
        String base=base();String document=upload(base,"Gradient descent minimizes loss.");
        assertEquals("200",service.grantDocument(document,10,1,true,false).getCode());
        login(3);
        assertTrue(data(service.courseContent(10,document,1)).get("content").toString().contains("Gradient descent"));
        assertEquals("403",assertThrows(RagAccessException.class,()->service.courseOriginalFile(10,document,1)).code());
        assertEquals("403",service.getDocument(document).getCode());
        login(1);
        var replacement=data(service.replaceDocument(document,bytes("Photosynthesis converts solar energy."),"biology.txt"));
        assertEquals(2,((Number)replacement.get("document_version")).intValue());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=?",Integer.class,document));
        assertEquals(true,data(service.replaceDocument(document,bytes("Photosynthesis converts solar energy."),"same.txt")).get("duplicate"));
        login(3);
        assertTrue(data(service.courseContent(10,document,1)).get("content").toString().contains("Gradient descent"));
        assertEquals("403",service.courseContent(10,document,2).getCode());
        login(1);assertEquals("200",service.grantDocument(document,10,2,true,true).getCode());
        login(3);assertEquals("biology.txt",service.courseOriginalFile(10,document,2).filename());
        assertEquals("403",service.courseContent(20,document,2).getCode());
        login(1);assertEquals("200",service.revokeDocumentGrant(document,10,1).getCode());
        login(3);assertEquals("403",service.courseContent(10,document,1).getCode());
        courseMembers.clear();assertEquals("403",service.courseContent(10,document,2).getCode());
        assertEquals("403",assertThrows(RagAccessException.class,()->service.courseOriginalFile(10,document,2)).code());
    }

    @Test void courseMembersSearchOnlyMaterialsGrantedToThatCourse() {
        String base=base();
        String granted=upload(base,"Gradient descent minimizes training loss.");
        upload(base,"Gradient boosting builds an ensemble of trees.");
        assertEquals("200",service.grantDocument(granted,10,1,true,false).getCode());
        login(3);
        var found=(RagSearchResponse)service.searchForCourse(10,"gradient",3,5).getData();
        assertFalse(found.hits().isEmpty());
        assertTrue(found.hits().stream().allMatch(hit->hit.documentId().equals(granted)));
        @SuppressWarnings("unchecked") var materials=(List<Map<String,Object>>)service.courseMaterials(10).getData();
        assertEquals(1,materials.size());assertEquals(granted,materials.getFirst().get("document_id"));
        assertEquals(1,((Number)materials.getFirst().get("searchable")).intValue());
        assertEquals("403",service.searchForCourse(20,"gradient",3,5).getCode());
        login(1);service.revokeDocumentGrant(granted,10,1);
        login(3);assertTrue(((RagSearchResponse)service.searchForCourse(10,"gradient",3,5).getData()).hits().isEmpty());
        courseMembers.clear();assertEquals("403",service.searchForCourse(10,"gradient",3,5).getCode());
    }

    @Test void onlyDocumentOwnerWithCourseMaintenanceRightsCanGrantAndBindingDoesNotGrant() {
        String base=base();String document=upload(base,"Private draft.");
        assertEquals("403",service.grantDocument(document,20,1,true,true).getCode());
        login(2);assertEquals("403",service.grantDocument(document,20,1,true,true).getCode());
        login(9);assertEquals("403",service.grantDocument(document,10,1,true,true).getCode());
        String scope=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO native_rag_binding_scope(id,course_id,scope_type,scope_id,purpose,binding_mode,revision) VALUES(?,10,'COURSE',10,'LEARNING','OVERRIDE',1)",scope);
        jdbc.update("INSERT INTO native_rag_binding_item(id,scope_id,source_type,document_id,document_version,ordinal_number) VALUES(?,?,'DOCUMENT',?,1,1)",UUID.randomUUID().toString(),scope,document);
        login(3);assertEquals("403",service.courseContent(10,document,1).getCode());
    }

    @Test void deletingAMultiVersionDocumentRevokesAccessAndRetriesFailedPrivateFileRemoval() throws Exception {
        String base=base();String document=upload(base,"First version.");
        service.replaceDocument(document,bytes("Second version."),"second.txt");
        service.grantDocument(document,10,2,true,true);
        doThrow(new RagAccessException("500","simulated delete failure")).when(files).delete(anyString());
        assertEquals("200",service.deleteDocument(document).getCode());
        assertEquals("404",service.getDocument(document).getCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_file_cleanup WHERE state='PENDING'",Integer.class));
        try(var paths=Files.list(temp.resolve("private"))){assertEquals(2,paths.count());}
        doCallRealMethod().when(files).delete(anyString());
        assertEquals(2,service.retryFileCleanup());
        try(var paths=Files.list(temp.resolve("private"))){assertEquals(0,paths.count());}
    }

    @Test void databaseFailureAndFileRemovalFailureAreCompensatedWithoutPublishingPartialData() {
        String base=base();
        jdbc.execute("ALTER TABLE native_rag_chunk ADD CONSTRAINT fail_second CHECK(ordinal_number<=1)");
        doThrow(new RagAccessException("500","simulated cleanup failure")).when(files).delete(anyString());
        assertEquals("500",service.uploadFile(base,bytes("Gradient descent is useful.\n".repeat(90)),"many.txt").getCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_chunk",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_file_cleanup WHERE state='PENDING'",Integer.class));
    }

    @Test void vectorRebuildPublishesANewIndexAndFailurePreservesTheActiveOne() {
        String base=base();String document=upload(base,"Gradient descent minimizes loss.");
        String old=data(service.getDocument(document)).get("active_index_version").toString();
        when(embedding.isConfigured()).thenReturn(true);when(embedding.isRequested()).thenReturn(true);
        when(embedding.embed(anyList())).thenAnswer(call->((List<?>)call.getArgument(0)).stream().map(ignored->new double[]{1,0}).toList());
        assertEquals("200",service.reindexDocument(document).getCode());
        String current=data(service.getDocument(document)).get("active_index_version").toString();assertNotEquals(old,current);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_index_version",Integer.class));
        assertEquals(1,((List<?>)service.chunks(document).getData()).size());
        when(embedding.embed(anyList())).thenThrow(new IllegalStateException("provider failure"));
        assertEquals("503",service.reindexDocument(document).getCode());
        assertEquals(current,data(service.getDocument(document)).get("active_index_version"));
        assertEquals(1,((List<?>)service.chunks(document).getData()).size());
    }

    @Test void failedReplacementKeepsTheOriginalVersionFileAndActiveIndex() throws Exception {
        String base=base();String document=upload(base,"Verified original source.");
        String index=data(service.getDocument(document)).get("active_index_version").toString();
        jdbc.execute("ALTER TABLE native_rag_chunk ADD CONSTRAINT failed_replacement CHECK(ordinal_number<=1)");
        assertEquals("500",service.replaceDocument(document,bytes("Large replacement content.\n".repeat(100)),"replacement.txt").getCode());
        var current=data(service.getDocument(document));
        assertEquals(1,((Number)current.get("document_version")).intValue());
        assertEquals(index,current.get("active_index_version"));
        assertEquals("Verified original source.",Files.readString(service.originalFile(document).path()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version",Integer.class));
        try(var paths=Files.list(temp.resolve("private"))){assertEquals(1,paths.count());}
    }

    @Test void restartRecoversAnAbandonedWriteButNeverRemovesAReferencedOriginal() throws Exception {
        String orphan=UUID.randomUUID().toString();
        files.store(orphan,bytes("Uncommitted original."));
        jdbc.update("INSERT INTO native_rag_file_cleanup(file_key,state,created_at) VALUES(?,'STAGING',?)",orphan,
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1200)));
        String base=base();String document=upload(base,"Committed original.");
        String referenced=jdbc.queryForObject("SELECT file_key FROM native_rag_document WHERE id=?",String.class,document);
        jdbc.update("INSERT INTO native_rag_file_cleanup(file_key,state) VALUES(?,'PENDING')",referenced);
        service.initialize();
        assertFalse(Files.exists(temp.resolve("private").resolve(orphan)));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT state FROM native_rag_file_cleanup WHERE file_key=?",String.class,orphan));
        assertEquals("Committed original.",Files.readString(service.originalFile(document).path()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_file_cleanup WHERE file_key=?",Integer.class,referenced));
    }

    @Test void identityRevokedDuringEmbeddingCannotReceiveRetrievedPrivateText() {
        when(embedding.isConfigured()).thenReturn(true);when(embedding.isRequested()).thenReturn(true);
        when(embedding.embed(anyList())).thenAnswer(call->((List<?>)call.getArgument(0)).stream().map(ignored->new double[]{1,0}).toList());
        String base=base();upload(base,"Sensitive gradient descent source.");
        when(embedding.embed(anyList())).thenAnswer(call->{roles.put(1,"student");return List.of(new double[]{1,0});});
        var result=service.searchForOwner(List.of(base),"gradient descent",1,3);
        verify(embedding,times(2)).embed(anyList());
        assertEquals("403",result.getCode());assertNull(result.getData());
    }

    @Test void identityRevokedDuringOriginalVerificationCannotReceiveTheFilePath() {
        String base=base();String document=upload(base,"Private original.");
        doAnswer(call->{Object verified=call.callRealMethod();roles.put(1,"student");return verified;})
                .when(files).verified(anyString(),anyLong(),anyString());
        assertEquals("403",assertThrows(RagAccessException.class,()->service.originalFile(document)).code());
    }
}
