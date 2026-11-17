package com.cupk.aiacademy.rag;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseKnowledgeServiceTest {
    JdbcTemplate jdbc;
    CourseKnowledgeService service;
    @TempDir Path temp;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE t_course(id INT PRIMARY KEY,title VARCHAR(200),teacher_id INT,status INT)");
        jdbc.execute("CREATE TABLE native_rag_base(id VARCHAR(36) PRIMARY KEY,owner_id INT,name VARCHAR(200),description TEXT,shared BOOLEAN DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO t_course VALUES(1,'课程甲',7,2),(2,'课程乙',8,1)");
        var actor = new AuthUtils.LoginUser(); actor.setUserId(7); actor.setRoleCode("teacher"); UserContext.set(actor);
        service = new CourseKnowledgeService(jdbc, mock(PermissionService.class));
        service.initialize();
    }
    @AfterEach void clean() { UserContext.clear(); }
    @Test void independentIdempotentCourseLibrary() {
        var first = service.ensureCourseBase(1);
        assertEquals(first.get("id"), service.ensureCourseBase(1).get("id"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_base", Integer.class));
        assertEquals(1, first.get("course_id"));
    }
    @Test void otherTeacherCannotAccessLibrary() {
        assertThrows(RagAccessException.class, () -> service.ensureCourseBase(2));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_base", Integer.class));
    }
    @Test void studentCannotManageLibrary() {
        var actor = new AuthUtils.LoginUser(); actor.setUserId(7); actor.setRoleCode("student"); UserContext.set(actor);
        assertThrows(RagAccessException.class, () -> service.ensureCourseBase(1));
    }

    private NativeRagService nativeService() {
        var rag = new NativeRagService(jdbc,new RagDocumentParser(),mock(RagEmbeddingClient.class),new ObjectMapper(),temp.toString());
        rag.initialize();
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_ai_preparation(id BIGINT PRIMARY KEY,owned_knowledge_base_id VARCHAR(36),published_course_id INTEGER,config_json TEXT,knowledge_base_ids_json TEXT)");
        return rag;
    }

    @Test void studentAccessRequiresExplicitSelectionAndVersion() {
        var rag = nativeService();
        String kb = service.baseId(1);
        var upload = rag.uploadFile(kb,"课程资料".getBytes(java.nio.charset.StandardCharsets.UTF_8),"source.txt");
        String id = ((Map<?,?>)upload.getData()).get("id").toString();
        assertEquals(false,service.documentAccess(1).getFirst().get("read_excerpt"));
        service.updateDocumentAccess(1,List.of(id),true,true);
        assertEquals(true,service.documentAccess(1).getFirst().get("download_allowed"));
        jdbc.update("UPDATE native_rag_document SET document_version=2 WHERE id=?",id);
        assertEquals(false,service.documentAccess(1).getFirst().get("read_excerpt"));
        service.updateDocumentAccess(1,List.of(id),false,false);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant WHERE revoked_at IS NULL",Integer.class));
    }

    @Test void legacyAssignmentKeepsVersionsAndCopiesCrossCourseGrants() {
        var rag = nativeService();
        String legacy = ((Map<?,?>)rag.createKnowledgeBase("旧库","").getData()).get("id").toString();
        String doc = ((Map<?,?>)rag.uploadFile(legacy,"历史教材独立副本".getBytes(java.nio.charset.StandardCharsets.UTF_8),"legacy.txt").getData()).get("id").toString();
        jdbc.update("INSERT INTO native_rag_course_grant(id,owner_id,course_id,document_id,document_version,read_excerpt,download_allowed) VALUES(?,7,2,?,1,TRUE,FALSE)",UUID.randomUUID().toString(),doc);
        var legacyService = new LegacyCourseLibraryService(jdbc,service,new ObjectMapper(),new RagFileStore(temp.toString()));
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> legacyService.assign(legacy,1,null));
        assertEquals(service.baseId(1),jdbc.queryForObject("SELECT kb_id FROM native_rag_document WHERE id=?",String.class,doc));
        var copy = jdbc.queryForMap("SELECT d.* FROM native_rag_document d JOIN course_knowledge_base c ON d.kb_id=c.kb_id WHERE c.course_id=2");
        assertNotEquals(doc,copy.get("id"));
        assertNotEquals(jdbc.queryForObject("SELECT file_key FROM native_rag_document WHERE id=?",String.class,doc),copy.get("file_key"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document_version WHERE document_id=?",Integer.class,copy.get("id")));
        assertEquals(copy.get("id"),jdbc.queryForObject("SELECT document_id FROM native_rag_course_grant WHERE course_id=2",String.class));
        assertTrue(legacyService.list().isEmpty());
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> legacyService.rollback(legacy));
        assertEquals(legacy,jdbc.queryForObject("SELECT kb_id FROM native_rag_document WHERE id=?",String.class,doc));
        assertEquals(doc,jdbc.queryForObject("SELECT document_id FROM native_rag_course_grant WHERE course_id=2",String.class));
        assertEquals(1,legacyService.list().size());
    }

    @Test void duplicateOriginalsFromDifferentLegacyLibrariesKeepIndependentHistories() {
        var rag = nativeService();
        var migration = new LegacyCourseLibraryService(jdbc,service,new ObjectMapper(),new RagFileStore(temp.toString()));
        for (int i=0;i<2;i++) {
            String base = ((Map<?,?>)rag.createKnowledgeBase("历史"+i,"").getData()).get("id").toString();
            assertEquals("200",rag.uploadFile(base,"同一原件".getBytes(java.nio.charset.StandardCharsets.UTF_8),"same.txt").getCode());
            new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> migration.assign(base,1,null));
        }
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document WHERE kb_id=?",Integer.class,service.baseId(1)));
        rag.initialize();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_document",Integer.class));
    }

    @Test void teachersSearchPrivateCourseMaterialsAndCannotGrantThemToAnotherCourse() {
        var rag = nativeService();
        rag.configureCourseKnowledge(service);
        String base = service.baseId(1);
        String id = ((Map<?,?>)rag.uploadFile(base,"专属课程中的树形结构".getBytes(java.nio.charset.StandardCharsets.UTF_8),"teacher.md").getData()).get("id").toString();
        var found = rag.search(new RagSearchRequest(List.of(),"树形结构",5,1));
        assertEquals("200",found.getCode());
        assertFalse(((RagSearchResponse)found.getData()).hits().isEmpty());
        assertEquals("403",rag.grantDocument(id,2,1,true,false).getCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM native_rag_course_grant",Integer.class));
    }

    @Test void legacyLibraryCanCreateItsCourseWithGeneratedTimestampColumns() {
        var rag = nativeService();
        jdbc.execute("DROP TABLE t_course");
        jdbc.execute("CREATE TABLE t_course(id INTEGER AUTO_INCREMENT PRIMARY KEY,title VARCHAR(100),description TEXT,teacher_id INTEGER,category_id INTEGER,status INTEGER,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE t_course_category(id INTEGER PRIMARY KEY)");
        jdbc.update("INSERT INTO t_course_category VALUES(1)");
        String legacy = ((Map<?,?>)rag.createKnowledgeBase("待归属资料","").getData()).get("id").toString();
        var migration = new LegacyCourseLibraryService(jdbc,service,new ObjectMapper(),new RagFileStore(temp.toString()));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var assigned = transaction.execute(status -> migration.assign(legacy,null,"由旧资料建立课程"));
        int id = ((Number)assigned.get("courseId")).intValue();
        assertEquals(2,jdbc.queryForObject("SELECT status FROM t_course WHERE id=?",Integer.class,id));
        assertEquals(7,jdbc.queryForObject("SELECT teacher_id FROM t_course WHERE id=?",Integer.class,id));
        assertEquals(service.baseId(id),assigned.get("knowledgeBaseId"));
        assertEquals(assigned.get("courseId"),transaction.execute(status -> migration.assign(legacy,null,"由旧资料建立课程")).get("courseId"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_course",Integer.class));
    }

    @Test void terminalJobWithRetainedProcessingVersionDoesNotBlockAssignment() {
        var rag = nativeService();
        var migration = new LegacyCourseLibraryService(jdbc,service,new ObjectMapper(),new RagFileStore(temp.toString()));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        for (String stage : List.of("COMPLETED","FAILED","CANCELLED")) {
            String legacy = ((Map<?,?>)rag.createKnowledgeBase("历史任务"+stage,"").getData()).get("id").toString();
            String doc = ((Map<?,?>)rag.uploadFile(legacy,stage.getBytes(java.nio.charset.StandardCharsets.UTF_8),stage+".txt").getData()).get("id").toString();
            String job = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO native_rag_ingestion_job(id,actor_id,document_id,document_version,stage) VALUES(?,7,?,1,?)",job,doc,stage);
            jdbc.update("UPDATE native_rag_document SET processing_version=1,processing_job_id=?,processing_state='READY' WHERE id=?",job,doc);
            var assigned = transaction.execute(status -> migration.assign(legacy,1,null));
            assertEquals(1,assigned.get("courseId"));
            assertEquals(service.baseId(1),jdbc.queryForObject("SELECT kb_id FROM native_rag_document WHERE id=?",String.class,doc));
        }
    }

    @Test void activeProcessingJobIsCheckedBeforeMovingAnyDocuments() {
        var rag = nativeService();
        String legacy = ((Map<?,?>)rag.createKnowledgeBase("处理中资料","").getData()).get("id").toString();
        String doc = ((Map<?,?>)rag.uploadFile(legacy,"正文".getBytes(java.nio.charset.StandardCharsets.UTF_8),"active.txt").getData()).get("id").toString();
        String job = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO native_rag_ingestion_job(id,actor_id,document_id,document_version,stage) VALUES(?,7,?,1,'OCR')",job,doc);
        jdbc.update("UPDATE native_rag_document SET processing_version=1,processing_job_id=?,processing_state='OCR' WHERE id=?",job,doc);
        var migration = new LegacyCourseLibraryService(jdbc,service,new ObjectMapper(),new RagFileStore(temp.toString()));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var error = assertThrows(RagAccessException.class,() -> transaction.execute(status -> migration.assign(legacy,1,null)));
        assertEquals("409",error.code());
        assertEquals(legacy,jdbc.queryForObject("SELECT kb_id FROM native_rag_document WHERE id=?",String.class,doc));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM course_library_assignment",Integer.class));
    }
}
