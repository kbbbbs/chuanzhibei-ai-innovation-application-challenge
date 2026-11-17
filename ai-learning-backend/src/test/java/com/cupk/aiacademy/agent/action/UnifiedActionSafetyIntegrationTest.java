package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.*;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedActionSafetyIntegrationTest {
    JdbcTemplate db;
    AgentActionSafetyService safety;
    ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    TransactionTemplate transactions;
    @BeforeEach void setup() {
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa","");
        db=new JdbcTemplate(data);
        transactions=new TransactionTemplate(new DataSourceTransactionManager(data));
        db.execute("CREATE TABLE t_course(id INT PRIMARY KEY,title VARCHAR(60),workflow_version INT)");
        db.execute("CREATE TABLE t_chapter(id INT PRIMARY KEY,course_id INT,title VARCHAR(60))");
        db.execute("CREATE TABLE t_lesson(id INT PRIMARY KEY,chapter_id INT,content VARCHAR(60))");
        db.execute("CREATE TABLE t_agent_session(id INT PRIMARY KEY,user_id INT,status INT DEFAULT 1,course_id INT)");
        db.execute("CREATE TABLE t_agent_pending_action(action_id VARCHAR(64) PRIMARY KEY,expires_at TIMESTAMP)");
        db.update("INSERT INTO t_course VALUES(129,'人工智能基础',1),(130,'另一门课程',1)");
        db.update("INSERT INTO t_chapter VALUES(9,129,'第一章')");
        db.update("INSERT INTO t_lesson VALUES(8,9,'正文')");
        db.update("INSERT INTO t_agent_session(id,user_id,course_id) VALUES(4,2,129),(5,3,129)");
        safety=new AgentActionSafetyService(db,json); safety.initialize(); login(2);
    }
    @AfterEach void clear(){UserContext.clear();}
    void login(int id){var actor=new AuthUtils.LoginUser();actor.setUserId(id);actor.setRoleCode("teacher");UserContext.set(actor);}
    AgentPendingAction preview(Map<String,Object> params){var a=new AgentPendingAction(UUID.randomUUID().toString(),"lesson.update",2,params,LocalDateTime.now(),LocalDateTime.now().plusMinutes(5));safety.register(a);safety.bind(a.actionId(),4,129);return a;}
    @Test void changingParentCourseVersionInvalidatesLessonPreview(){
        var a=preview(Map.of("lessonId",8,"content","新正文"));
        db.update("UPDATE t_course SET workflow_version=2 WHERE id=129");
        assertThrows(BusinessException.class,()->transactions.execute(status->{safety.validateTargets(a);return null;}));
    }
    @Test void sessionBindingRejectsCrossCourseTargetsAndForeignSessions(){
        var a=preview(Map.of("lessonId",8,"content","新正文"));
        assertThrows(BusinessException.class,()->safety.bind(a.actionId(),4,130));
        assertThrows(BusinessException.class,()->safety.bind(a.actionId(),5,129));
        safety.bind(a.actionId(),4,129);
        assertEquals(129,db.queryForObject("SELECT course_id FROM t_agent_action_execution WHERE action_id=?",Integer.class,a.actionId()));
    }
    @Test void examAnswerTargetsRetainTheirCourseAndVersion() {
        db.execute("CREATE TABLE t_exam_paper(id INT PRIMARY KEY,course_id INT)");
        db.execute("CREATE TABLE t_exam_session(id INT PRIMARY KEY,course_id INT,paper_id INT)");
        db.execute("CREATE TABLE t_exam_record(id INT PRIMARY KEY,exam_session_id INT)");
        db.execute("CREATE TABLE t_exam_answer(id INT PRIMARY KEY,exam_record_id INT,score INT)");
        db.update("INSERT INTO t_exam_paper VALUES(1,130)");
        db.update("INSERT INTO t_exam_session VALUES(1,130,1)");
        db.update("INSERT INTO t_exam_record VALUES(1,1)");
        db.update("INSERT INTO t_exam_answer VALUES(2,1,0)");
        var action = new AgentPendingAction(UUID.randomUUID().toString(),"exam.answer.grade",2,
                Map.of("submissionId",1,"submissionAnswerId",2,"score",10),
                LocalDateTime.now(),LocalDateTime.now().plusMinutes(5));
        safety.register(action);
        assertThrows(BusinessException.class, () -> safety.bind(action.actionId(),4,129));
        db.update("UPDATE t_exam_answer SET score=5 WHERE id=2");
        assertThrows(BusinessException.class, () -> safety.validateTargets(action));
    }
    @Test void unboundPreviewCannotBeConfirmed() {
        var action = new AgentPendingAction(UUID.randomUUID().toString(),"lesson.update",2,
                Map.of("lessonId",8),LocalDateTime.now(),LocalDateTime.now().plusMinutes(5));
        safety.register(action);
        assertThrows(BusinessException.class, () -> safety.lockAndReadResult(action.actionId(),2));
    }
    @Test void sortingCapturesEachLessonRatherThanOnlyItsChapter() {
        var action = new AgentPendingAction(UUID.randomUUID().toString(),"lesson.sort",2,
                Map.of("chapterId",9,"items",List.of(Map.of("lessonId",8,"sortOrder",1))),
                LocalDateTime.now(),LocalDateTime.now().plusMinutes(5));
        safety.register(action);
        db.update("UPDATE t_lesson SET content='其他编辑' WHERE id=8");
        assertThrows(BusinessException.class, () -> safety.validateTargets(action));
    }
    @Test void normalizationCannotAddAnUnreviewedTarget() {
        var action = preview(Map.of("lessonId",8));
        assertThrows(BusinessException.class, () -> safety.validateExecutionParameters(action,
                Map.of("lessonId",8,"courseId",130)));
    }
    @Test void deletedSessionInvalidatesPendingAction() {
        var action = preview(Map.of("lessonId",8));
        safety.bind(action.actionId(),4,129);
        db.update("UPDATE t_agent_session SET status=0 WHERE id=4");
        assertThrows(BusinessException.class, () -> safety.lockAndReadResult(action.actionId(),2));
    }
    @Test void executionResultsSurviveStoreRecreation(){
        var a=preview(Map.of("lessonId",8));
        var result=new AgentActionExecutionResult("lesson.update","更新",true,"完成",Map.of("lessonId",8),LocalDateTime.now());
        safety.completed(a.actionId(),result);
        var recreated=new AgentActionSafetyService(db,json);recreated.initialize();
        assertEquals(result,transactions.execute(status->recreated.lockAndReadResult(a.actionId(),2).orElseThrow()));
        assertThrows(BusinessException.class,()->recreated.lockAndReadResult(a.actionId(),3));
    }
    @Test void unreviewedLegacyActionIsRejected(){
        assertThrows(BusinessException.class,()->safety.lockAndReadResult("legacy-action",2));
    }
    @Test void concurrentConfirmationsCommitExactlyOnce() throws Exception {
        var a=preview(Map.of("lessonId",8));var calls=new AtomicInteger();
        AgentActionExecutor executor=new AgentActionExecutor(){
            public String actionCode(){return "lesson.update";}
            public String actionName(){return "修改课时";}
            public AgentActionExecutionResult execute(AgentActionExecutionContext context){
                calls.incrementAndGet(); db.update("UPDATE t_lesson SET content='新正文' WHERE id=8");
                return new AgentActionExecutionResult(actionCode(),actionName(),true,"完成",Map.of("lessonId",8),LocalDateTime.now());
            }
        };
        AgentPendingActionStore store=new AgentPendingActionStore(){
            public AgentPendingAction create(String c,Integer u,Map<String,Object> p,Duration ttl){throw new UnsupportedOperationException();}
            public Optional<AgentPendingAction> findById(String id){return Optional.of(a);}
            public void remove(String id){}
            public Optional<AgentActionExecutionResult> lockAndReadResult(String id,Integer owner){return safety.lockAndReadResult(id,owner);}
            public void validateTargets(AgentPendingAction action){safety.validateTargets(action);}
            public void completed(String id,AgentActionExecutionResult result){safety.completed(id,result);}
        };
        var request=new AgentActionConfirmRequest("lesson.update",true,Map.of(),a.actionId());
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<AgentActionExecutionResult> task=()->{login(2);try{return transactions.execute(status->new AgentActionExecutionService(List.of(executor),mock(OperationLogMapper.class),json,store).confirm(request,"local"));}finally{UserContext.clear();}};
            var one=pool.submit(task);var two=pool.submit(task);
            assertEquals(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS));
        }
        assertEquals(1,calls.get());
    }
}
