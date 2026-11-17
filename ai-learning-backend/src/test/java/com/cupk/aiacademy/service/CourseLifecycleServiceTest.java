package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.rag.RagAccessException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseLifecycleServiceTest {
    JdbcTemplate jdbc;
    CourseLifecycleService service;
    CourseLifecycleSchema schema;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE t_course(id INT PRIMARY KEY,title VARCHAR(200),category_id INT,teacher_id INT,status INT,student_count INT DEFAULT 0,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE t_teaching_class(id INT PRIMARY KEY,course_id INT,teacher_id INT,class_name VARCHAR(80),semester VARCHAR(30),status INT)");
        jdbc.execute("CREATE TABLE t_class_student(id INT PRIMARY KEY,class_id INT,student_id INT,status INT)");
        jdbc.execute("CREATE TABLE t_ai_preparation(id BIGINT PRIMARY KEY,teacher_id INT,config_json TEXT,project_status VARCHAR(30),published_course_id INT)");
        jdbc.execute("CREATE TABLE t_chapter(id INT PRIMARY KEY,course_id INT,status INT)");
        jdbc.execute("CREATE TABLE t_lesson(id INT PRIMARY KEY,chapter_id INT,status INT)");
        jdbc.execute("CREATE TABLE course_preparation_application(project_id BIGINT PRIMARY KEY,course_id INT,rolled_back BOOLEAN)");
        jdbc.execute("CREATE TABLE t_exam_session(id INT PRIMARY KEY,class_id INT)");
        jdbc.execute("CREATE TABLE t_homework_session(id INT PRIMARY KEY,class_id INT)");
        jdbc.execute("CREATE TABLE t_homework(id INT PRIMARY KEY,class_id INT)");
        jdbc.execute("CREATE TABLE t_grade_record(id INT PRIMARY KEY,course_id INT,class_id INT)");
        jdbc.execute("CREATE TABLE t_exam_paper(id INT PRIMARY KEY,course_id INT)");
        jdbc.execute("CREATE TABLE t_homework_paper(id INT PRIMARY KEY,course_id INT)");
        jdbc.execute("CREATE TABLE t_question(id INT PRIMARY KEY,course_id INT)");
        jdbc.update("INSERT INTO t_course(id,title,category_id,teacher_id,status) VALUES(1,'人工智能基础',1,7,2),(2,'旧发布课程',1,8,1)");
        jdbc.update("INSERT INTO t_teaching_class VALUES(10,1,7,'一班','秋',1),(11,1,7,'二班','秋',1),(12,2,8,'其他课程班','秋',1)");
        jdbc.update("INSERT INTO t_class_student VALUES(1,10,21,1),(2,10,22,1),(3,11,22,1),(4,11,23,1)");
        schema = new CourseLifecycleSchema(jdbc); schema.initialize();
        // Course 1 has one lesson, so it may be published; an empty course may not. Added after migration so it stays a draft.
        jdbc.update("INSERT INTO t_chapter VALUES(100,1,1)");
        jdbc.update("INSERT INTO t_lesson VALUES(1000,100,1)");
        var actor = new AuthUtils.LoginUser(); actor.setUserId(7); actor.setRoleCode("teacher"); UserContext.set(actor);
        var mapper = mock(CourseMapper.class);
        when(mapper.selectById(anyInt())).thenAnswer(call -> {
            var row = jdbc.queryForMap("SELECT * FROM t_course WHERE id=?", (Integer)call.getArgument(0));
            var course = new Course(); course.setId(((Number)row.get("id")).intValue());
            course.setSetupStage((String)row.get("setup_stage")); course.setContentMode((String)row.get("content_mode"));
            course.setWorkflowVersion(((Number)row.get("workflow_version")).longValue());
            course.setStatus(((Number)row.get("status")).intValue());
            return course;
        });
        service = new CourseLifecycleService(jdbc, mapper, mock(PermissionService.class));
    }
    @AfterEach void clean() { UserContext.clear(); }
    @Test void explicitCompletionIsRequiredAndIdempotent() {
        var course = service.saveSetup(1, new CourseLifecycleService.SetupRequest("CONTENT", "MANUAL", null, 0L));
        assertEquals("CONTENT", course.getSetupStage());
        assertEquals(2, course.getStatus());
        var completed = service.complete(1, new CourseLifecycleService.VersionRequest(1L));
        assertEquals("COMPLETED", completed.getSetupStage());
        assertEquals(2L, completed.getWorkflowVersion());
        assertEquals(2L, service.complete(1, new CourseLifecycleService.VersionRequest(1L)).getWorkflowVersion());
    }
    @Test void publicationRequiresCompletionAndClassesAndCannotRebindOtherCourse() {
        assertThrows(RagAccessException.class, () -> service.publish(1, new CourseLifecycleService.PublishRequest(List.of(10), 0L)));
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED' WHERE id=1");
        assertThrows(RagAccessException.class, () -> service.publish(1, new CourseLifecycleService.PublishRequest(List.of(), 0L)));
        assertThrows(RagAccessException.class, () -> service.publish(1, new CourseLifecycleService.PublishRequest(List.of(12), 0L)));
        assertEquals(2, jdbc.queryForObject("SELECT status FROM t_course WHERE id=1", Integer.class));
    }
    @Test void publishesSelectedClassOnlyAndRetriesWithoutDuplicating() {
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED' WHERE id=1");
        var result = service.publish(1, new CourseLifecycleService.PublishRequest(List.of(10,10), 0L));
        assertEquals(List.of(10), result.get("classIds"));
        assertEquals(false, jdbc.queryForObject("SELECT course_open FROM t_teaching_class WHERE id=11", Boolean.class));
        assertEquals(2, jdbc.queryForObject("SELECT student_count FROM t_course WHERE id=1", Integer.class));
        assertEquals(result.get("workflowVersion"), service.publish(1, new CourseLifecycleService.PublishRequest(List.of(10), 0L)).get("workflowVersion"));
        assertThrows(RagAccessException.class, () -> service.publish(1, new CourseLifecycleService.PublishRequest(List.of(11), 0L)));
    }
    @Test void staleSetupDoesNotOverwriteAndAiMustHaveApplied() {
        jdbc.update("INSERT INTO t_ai_preparation VALUES(33,7,'{\"targetCourseId\":1}','DRAFT',NULL)");
        service.saveSetup(1,new CourseLifecycleService.SetupRequest("CONTENT","AI",33L,0L));
        assertThrows(RagAccessException.class, () -> service.saveSetup(1,new CourseLifecycleService.SetupRequest("MATERIALS",null,null,0L)));
        assertThrows(RagAccessException.class, () -> service.complete(1,new CourseLifecycleService.VersionRequest(1L)));
        jdbc.update("UPDATE t_ai_preparation SET project_status='PUBLISHED',published_course_id=1 WHERE id=33");
        jdbc.update("INSERT INTO course_preparation_application VALUES(33,1,TRUE)");
        assertThrows(RagAccessException.class, () -> service.complete(1,new CourseLifecycleService.VersionRequest(1L)));
        jdbc.update("UPDATE course_preparation_application SET rolled_back=FALSE WHERE project_id=33");
        assertEquals("COMPLETED", service.complete(1,new CourseLifecycleService.VersionRequest(1L)).getSetupStage());
    }
    @Test void administratorCanRevokeAllClassesButCannotUseTeacherCreationActions() {
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED' WHERE id=1");
        service.publish(1,new CourseLifecycleService.PublishRequest(List.of(10,11),0L));
        UserContext.get().setRoleCode("admin");
        service.updatePublication(1,new CourseLifecycleService.PublishRequest(List.of(),1L));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM t_teaching_class WHERE course_id=1 AND course_open=TRUE",Integer.class));
        assertThrows(RagAccessException.class, () -> service.complete(1,new CourseLifecycleService.VersionRequest(2L)));
    }
    @Test void migrationPreservesPublishedAccessAndBacksUpOnlyOnce() {
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT setup_stage FROM t_course WHERE id=2",String.class));
        assertEquals(true,jdbc.queryForObject("SELECT course_open FROM t_teaching_class WHERE id=12",Boolean.class));
        assertEquals("MATERIALS",jdbc.queryForObject("SELECT setup_stage FROM t_course WHERE id=1",String.class));
        schema.initialize();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM course_workflow_migration_backup",Integer.class));
    }
    @Test void basicEditingUsesTheLockingFreshReadBeforePermissionChecks() {
        var mapper = mock(CourseMapper.class);
        var permissions = mock(PermissionService.class);
        var current = new Course(); current.setId(1); current.setStatus(1); current.setWorkflowVersion(8L);
        when(mapper.selectForUpdate(1)).thenReturn(current);
        var editing = new CourseLifecycleService(jdbc,mapper,permissions);
        assertSame(current,editing.basicForUpdate(1));
        var ordered = inOrder(mapper,permissions);
        ordered.verify(mapper).selectForUpdate(1);
        ordered.verify(permissions).requireCourseTeacher(current);
        verify(mapper,never()).selectById(anyInt());
    }
    @Test void partialDatabaseFailureRollsBackClassGrants() {
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED' WHERE id=1");
        jdbc.execute("ALTER TABLE t_course ADD CONSTRAINT test_failed_publish CHECK(id<>1 OR status<>1)");
        var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> tx.execute(status -> service.publish(1,new CourseLifecycleService.PublishRequest(List.of(10),0L))));
        assertEquals(false,jdbc.queryForObject("SELECT course_open FROM t_teaching_class WHERE id=10",Boolean.class));
        assertEquals(0L,jdbc.queryForObject("SELECT workflow_version FROM t_course WHERE id=1",Long.class));
    }
    @Test void migrationRestoresPendingAiAndKeepsAmbiguousEmptyCourseInDrafts() {
        jdbc.update("INSERT INTO t_course(id,title,category_id,teacher_id,status,setup_stage) VALUES(3,'待应用课程',1,7,2,NULL)");
        jdbc.update("INSERT INTO t_ai_preparation VALUES(34,7,'{\"targetCourseId\":3}','DRAFT',NULL)");
        schema.initialize();
        assertEquals("CONTENT",jdbc.queryForObject("SELECT setup_stage FROM t_course WHERE id=3",String.class));
        assertEquals(34L,jdbc.queryForObject("SELECT preparation_id FROM t_course WHERE id=3",Long.class));
    }
    @Test void courseDeletionClearsOnlyItsClassesAndMembershipsWithoutDependingOnForeignKeys() {
        service.deleteCourseClasses(1);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM t_teaching_class WHERE course_id=1",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM t_class_student",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_teaching_class WHERE id=12",Integer.class));
    }
    @Test void aCourseWithoutLessonsCannotBePublished() {
        jdbc.update("INSERT INTO t_course(id,title,category_id,teacher_id,status) VALUES(4,'空课程',1,7,2)");
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED' WHERE id=4");
        jdbc.update("INSERT INTO t_teaching_class(id,course_id,teacher_id,class_name,semester,status) VALUES(40,4,7,'空课程班','秋',1)");
        jdbc.update("INSERT INTO t_chapter VALUES(400,4,1)");
        var refused = assertThrows(RagAccessException.class, () -> service.publish(4, new CourseLifecycleService.PublishRequest(List.of(40), 0L)));
        assertTrue(refused.getMessage().contains("课时"));
        assertEquals(2, jdbc.queryForObject("SELECT status FROM t_course WHERE id=4", Integer.class));
    }
    @Test void deletionIsRefusedWhileTeachingRecordsWouldBeLost() {
        service.requireDeletable(1);
        jdbc.update("INSERT INTO t_exam_session VALUES(1,10)");
        jdbc.update("INSERT INTO t_question VALUES(1,1)");
        var refused = assertThrows(RagAccessException.class, () -> service.requireDeletable(1));
        assertEquals("409", refused.code());
        assertTrue(refused.getMessage().contains("考试 1 场"));
        assertTrue(refused.getMessage().contains("题目 1 道"));
        service.requireDeletable(2);
    }
    @Test void closingClassesOnUnpublishRemovesStudentAccess() {
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED' WHERE id=1");
        service.publish(1, new CourseLifecycleService.PublishRequest(List.of(10, 11), 0L));
        service.closeClasses(1);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM t_teaching_class WHERE course_id=1 AND course_open=TRUE", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT student_count FROM t_course WHERE id=1", Integer.class));
    }
    private void waitingClass() {
        jdbc.update("INSERT INTO t_teaching_class(id,course_id,teacher_id,class_name,semester,status,course_open) VALUES(13,NULL,7,'人工智能一班','秋',1,FALSE)");
        jdbc.update("INSERT INTO t_class_student VALUES(5,13,24,1)");
        jdbc.update("UPDATE t_course SET setup_stage='COMPLETED' WHERE id=1");
    }

    @Test void candidatesIncludeOwnedWaitingClassWithExistingStudentCount() {
        waitingClass();
        jdbc.update("INSERT INTO t_teaching_class(id,course_id,teacher_id,class_name,status,course_open) VALUES(14,NULL,8,'其他教师待关联班',1,FALSE)");
        var candidates = (List<Map<String,Object>>) service.publication(1).get("classes");
        assertEquals(List.of(10,11,13), candidates.stream().map(row -> row.get("id")).toList());
        assertEquals(1L, ((Number)candidates.get(2).get("studentCount")).longValue());
        assertNull(jdbc.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=13",Integer.class));
    }

    @Test void publicationBindsWaitingClassAndRetainsMembershipAlongsideExistingClass() {
        waitingClass();
        var result = service.publish(1,new CourseLifecycleService.PublishRequest(List.of(10,13),0L));
        assertEquals(List.of(10,13),result.get("classIds"));
        assertEquals(1,jdbc.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=13",Integer.class));
        assertEquals(true,jdbc.queryForObject("SELECT course_open FROM t_teaching_class WHERE id=13",Boolean.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_class_student WHERE class_id=13 AND student_id=24 AND status=1",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT student_count FROM t_course WHERE id=1",Integer.class));
        assertEquals(result.get("workflowVersion"),service.publish(1,new CourseLifecycleService.PublishRequest(List.of(10,13),0L)).get("workflowVersion"));
    }

    @Test void foreignTeacherAndAlreadyAssignedClassesCannotBeClaimed() {
        waitingClass();
        jdbc.update("INSERT INTO t_teaching_class(id,course_id,teacher_id,class_name,status,course_open) VALUES(14,NULL,8,'其他教师待关联班',1,FALSE),(15,2,7,'已关联其他课程',1,FALSE)");
        assertThrows(RagAccessException.class,()->service.publish(1,new CourseLifecycleService.PublishRequest(List.of(13,14),0L)));
        assertThrows(RagAccessException.class,()->service.publish(1,new CourseLifecycleService.PublishRequest(List.of(15),0L)));
        assertNull(jdbc.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=13",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=15",Integer.class));
    }

    @Test void stalePreviewDoesNotBindTheWaitingClass() {
        waitingClass();
        assertThrows(RagAccessException.class,()->service.publish(1,new CourseLifecycleService.PublishRequest(List.of(13),9L)));
        assertNull(jdbc.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=13",Integer.class));
        assertFalse(jdbc.queryForObject("SELECT course_open FROM t_teaching_class WHERE id=13",Boolean.class));
    }

    @Test void failedPublicationRollsBackCourseAssignmentAndOpening() {
        waitingClass();
        jdbc.execute("ALTER TABLE t_course ADD CONSTRAINT failed_waiting_publish CHECK(id<>1 OR status<>1)");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->transaction.execute(status ->
                service.publish(1,new CourseLifecycleService.PublishRequest(List.of(13),0L))));
        assertNull(jdbc.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=13",Integer.class));
        assertFalse(jdbc.queryForObject("SELECT course_open FROM t_teaching_class WHERE id=13",Boolean.class));
    }

    @Test void competingCoursesCannotBothAcquireOneWaitingClass() throws Exception {
        waitingClass();
        jdbc.update("INSERT INTO t_course(id,title,category_id,teacher_id,status,setup_stage,workflow_version) VALUES(3,'另一门本人课程',1,7,2,'COMPLETED',0)");
        jdbc.update("INSERT INTO t_chapter VALUES(101,3,1)");
        jdbc.update("INSERT INTO t_lesson VALUES(1001,101,1)");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first = () -> publishAfter(start,1);
            Callable<Boolean> second = () -> publishAfter(start,3);
            var a=executor.submit(first); var b=executor.submit(second);
            start.countDown();
            int successful = (a.get(10,TimeUnit.SECONDS) ? 1 : 0) + (b.get(10,TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1,successful);
        }
        assertTrue(List.of(1,3).contains(jdbc.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=13",Integer.class)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_course WHERE id IN (1,3) AND status=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_class_student WHERE class_id=13 AND student_id=24",Integer.class));
    }

    private boolean publishAfter(CountDownLatch start,int courseId) throws Exception {
        var actor=new AuthUtils.LoginUser(); actor.setUserId(7); actor.setRoleCode("teacher"); UserContext.set(actor);
        try {
            start.await();
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
            transaction.execute(status -> service.publish(courseId,new CourseLifecycleService.PublishRequest(List.of(13),0L)));
            return true;
        } catch (RagAccessException rejected) {
            return false;
        } finally { UserContext.clear(); }
    }
}
