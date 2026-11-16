package com.cupk.aiacademy.rag.graph;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.rag.*;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GraphServiceTest {
    JdbcTemplate db;
    GraphService service;
    Neo4jGraphRepository graph;
    PermissionService permissions;
    boolean granted = true;
    @BeforeEach void setup() {
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", ""));
        db.execute("CREATE TABLE t_course(id INT PRIMARY KEY,teacher_id INT,status INT)");
        db.execute("CREATE TABLE t_chapter(id INT PRIMARY KEY,course_id INT,title VARCHAR(100),status INT)");
        db.update("INSERT INTO t_course VALUES(10,1,1)");
        db.update("INSERT INTO t_chapter VALUES(20,10,'Chapter',1)");
        new RagSchemaMigrator(db).migrate();
        db.update("INSERT INTO native_rag_base(id,owner_id,name,description) VALUES('kb',1,'Source','')");
        db.update("INSERT INTO native_rag_document(id,kb_id,title,file_name,file_key,sha256,mime_type,file_size,page_count,chunk_count,embedding_status,active_index_version) VALUES('doc','kb','Chapter','chapter.pdf','file','abc','application/pdf',20,2,2,'ready','idx')");
        db.update("INSERT INTO native_rag_document_version(document_id,document_version,file_key,sha256,file_name,mime_type,file_size,actor_id,active_index_version) VALUES('doc',1,'file','abc','chapter.pdf','application/pdf',20,1,'idx')");
        db.update("INSERT INTO native_rag_index_version(id,document_id,document_version,parse_revision,chunk_revision,state) VALUES('idx','doc',1,1,'test','KEYWORD_READY')");
        db.update("INSERT INTO native_rag_chunk(id,document_id,kb_id,page_number,ordinal_number,content,index_version,char_start,char_end) VALUES('chunk','doc','kb',1,1,'Supervised learning includes classification and regression.','idx',0,60)");
        db.update("INSERT INTO native_rag_course_grant(id,owner_id,course_id,document_id,document_version,read_excerpt) VALUES('grant',1,10,'doc',1,TRUE)");
        permissions = mock(PermissionService.class);
        when(permissions.resolveActiveUser(anyInt())).thenAnswer(i -> user(i.getArgument(0), i.<Integer>getArgument(0)==1 ? "teacher" : "student"));
        when(permissions.canReadCourseMaterials(any(),eq(10))).thenAnswer(i -> granted);
        graph = mock(Neo4jGraphRepository.class);
        service = new GraphService(db,new ObjectMapper(),new RagAuthorizationService(permissions,db),graph);
        login(1,"teacher");
    }
    @AfterEach void clear() { UserContext.clear(); }
    @Test void createsTraceableDraftPublishesAndReadsOnlyApprovedVersion() {
        var created = create(); String id = created.get("graphId").toString();
        assertEquals(1,created.get("revision"));
        assertEquals("chunk",((List<Map<String,Object>>)service.sources(id).get("chunks")).getFirst().get("id"));
        var saved = service.saveDraft(id,new GraphService.DraftForm(1,nodes(),edges()));
        assertEquals(2,saved.get("revision"));
        var job=service.publish(id,2);
        assertEquals("PROJECTING",job.get("status"));
        service.processPending();
        verify(graph).project(eq(id),eq(2),anyList(),anyList());
        when(graph.read(id,2,100)).thenReturn(new GraphSlice(nodes(),edges(),false));
        login(2,"student");
        assertEquals(2,service.get(id,false).get("graphVersion"));
        assertEquals("chunk",service.evidence(id,"chunk").get("id"));
        assertThrows(RagAccessException.class,()->service.get(id,true));
        assertThrows(RagAccessException.class,()->service.saveDraft(id,new GraphService.DraftForm(2,nodes(),edges())));
    }
    @Test void rejectsForgedEvidenceDanglingEdgesAndStaleRevision() {
        String id=create().get("graphId").toString();
        var forged=new ArrayList<>(nodes());var bad=new LinkedHashMap<>(forged.getFirst());bad.put("evidenceIds",List.of("other-course-chunk"));forged.set(0,bad);
        assertThrows(RagAccessException.class,()->service.saveDraft(id,new GraphService.DraftForm(1,forged,edges())));
        var dangling=List.<Map<String,Object>>of(Map.of("id","edge","source","missing","target","b","type","HAS_TASK","label","任务类型","explanation","includes","origin","DOCUMENT","evidenceIds",List.of("chunk")));
        assertThrows(RagAccessException.class,()->service.saveDraft(id,new GraphService.DraftForm(1,nodes(),dangling)));
        service.saveDraft(id,new GraphService.DraftForm(1,nodes(),edges()));
        assertThrows(RagAccessException.class,()->service.saveDraft(id,new GraphService.DraftForm(1,nodes(),edges())));
        assertThrows(RagAccessException.class,()->service.publish(id,1));
    }
    @Test void revokeOrReindexDeniesAllPublishedGraphSurfaces() {
        String id=published();
        when(graph.read(id,2,100)).thenReturn(new GraphSlice(nodes(),edges(),false));
        login(2,"student");
        db.update("UPDATE native_rag_course_grant SET revoked_at=CURRENT_TIMESTAMP");
        assertThrows(RagAccessException.class,()->service.get(id,false));
        assertTrue(service.list(10).isEmpty());
        assertThrows(RagAccessException.class,()->service.evidence(id,"chunk"));
        assertThrows(RagAccessException.class,()->service.neighbors(id,"a",1,""));
        assertThrows(RagAccessException.class,()->service.paths(id,"a","b",false));
        db.update("UPDATE native_rag_course_grant SET revoked_at=NULL");
        db.update("UPDATE native_rag_document SET active_index_version='new-index'");
        assertThrows(RagAccessException.class,()->service.get(id,false));
    }
    @Test void failedProjectionKeepsOldPublishedVersionAndCanRetry() {
        String id=published();
        service.saveDraft(id,new GraphService.DraftForm(2,nodes(),edges()));
        var job=service.publish(id,3);String jobId=job.get("jobId").toString();
        doThrow(new RagAccessException("503","图服务不可用")).when(graph).project(eq(id),eq(3),anyList(),anyList());
        service.processPending();
        assertEquals("FAILED",service.job(id,jobId).get("status"));
        assertEquals(2,db.queryForObject("SELECT published_version FROM native_graph_definition WHERE id=?",Integer.class,id));
        doNothing().when(graph).project(eq(id),eq(3),anyList(),anyList());
        service.retry(id,jobId);service.processPending();
        assertEquals("DONE",service.job(id,jobId).get("status"));
        assertEquals(3,db.queryForObject("SELECT published_version FROM native_graph_definition WHERE id=?",Integer.class,id));
    }
    @Test void deniesWrongCourseChapterAndTeacherAndCycles() {
        assertThrows(RagAccessException.class,()->service.create(new GraphService.CreateForm(10,21,"Graph","doc",1,1,2)));
        String id=create().get("graphId").toString();
        var cyclic=List.<Map<String,Object>>of(teacherEdge("e1","a","b"),teacherEdge("e2","b","a"));
        assertThrows(RagAccessException.class,()->service.saveDraft(id,new GraphService.DraftForm(1,nodes(),cyclic)));
        db.update("UPDATE t_course SET teacher_id=3 WHERE id=10");
        assertThrows(RagAccessException.class,()->service.saveDraft(id,new GraphService.DraftForm(1,nodes(),edges())));
    }
    @Test void rechecksAuthorizationAfterGraphReadAndBlocksEditingProjection() {
        String id=published();
        when(graph.read(id,2,100)).thenAnswer(i->{granted=false;return new GraphSlice(nodes(),edges(),false);});
        assertThrows(RagAccessException.class,()->service.get(id,false));
        granted=true;
        service.saveDraft(id,new GraphService.DraftForm(2,nodes(),edges()));service.publish(id,3);
        assertThrows(RagAccessException.class,()->service.saveDraft(id,new GraphService.DraftForm(3,nodes(),edges())));
    }
    @Test void expiredWorkerCannotOverwriteAnotherWorkersCompletion() {
        String id=create().get("graphId").toString();
        service.saveDraft(id,new GraphService.DraftForm(1,nodes(),edges()));
        String jobId=service.publish(id,2).get("jobId").toString();
        doAnswer(i->{
            db.update("UPDATE native_graph_job SET lease_token='new-worker',status='DONE' WHERE id=?",jobId);
            db.update("UPDATE native_graph_definition SET state='PUBLISHED',published_version=2 WHERE id=?",id);
            return null;
        }).when(graph).project(eq(id),eq(2),anyList(),anyList());
        service.processPending();
        assertEquals("DONE",service.job(id,jobId).get("status"));
        assertEquals("PUBLISHED",db.queryForObject("SELECT state FROM native_graph_definition WHERE id=?",String.class,id));
    }
    Map<String,Object> create(){return service.create(new GraphService.CreateForm(10,20,"Machine learning","doc",1,1,2));}
    String published(){String id=create().get("graphId").toString();service.saveDraft(id,new GraphService.DraftForm(1,nodes(),edges()));service.publish(id,2);service.processPending();return id;}
    static List<Map<String,Object>> nodes(){return List.of(Map.of("id","a","name","监督学习","type","CONCEPT","description","Learning from labelled data","aliases",List.of("supervised"),"evidenceIds",List.of("chunk")),Map.of("id","b","name","分类","type","TASK","description","classification","aliases",List.of(),"evidenceIds",List.of("chunk")));}
    static List<Map<String,Object>> edges(){return List.of(Map.of("id","edge","source","a","target","b","type","HAS_TASK","label","任务类型","explanation","includes classification","origin","DOCUMENT","evidenceIds",List.of("chunk")));}
    static Map<String,Object> teacherEdge(String id,String from,String to){return Map.of("id",id,"source",from,"target",to,"type","PREREQUISITE_FOR","label","先修","explanation","Teacher reviewed sequence","origin","TEACHER","evidenceIds",List.of());}
    static AuthUtils.LoginUser user(int id,String role){var u=new AuthUtils.LoginUser();u.setUserId(id);u.setRoleCode(role);return u;}
    static void login(int id,String role){UserContext.set(user(id,role));}
}
