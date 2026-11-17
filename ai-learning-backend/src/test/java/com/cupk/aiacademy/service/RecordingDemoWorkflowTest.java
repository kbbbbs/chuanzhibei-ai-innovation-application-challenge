package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.PasswordUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.controller.CourseController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Isolated relational fixture and the same course access paths used by the UI. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recording-workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:contest-demo-schema.sql"
})
class RecordingDemoWorkflowTest {
    @Autowired JdbcTemplate db;
    @Autowired CourseResourceService courses;
    @Autowired CourseController controller;
    @Autowired PermissionService permissions;
    private static final int STUDENT = 1003902;
    private static final int DRAFT = 1003912;

    @BeforeEach void setup() throws Exception {
        seed();
        db.update("UPDATE t_course SET status=2 WHERE id=?",DRAFT);
        db.update("UPDATE t_teaching_class SET status=1 WHERE id=1003932");
        db.update("UPDATE t_class_student SET status=1 WHERE class_id=1003932 AND student_id=?",STUDENT);
        UserContext.set(student());
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void suppliedAccountsHaveValidSyntheticPasswordAndThreeMemberships() {
        for (String username : List.of("demo_teacher","demo_student"))
            assertTrue(PasswordUtils.matches("Demo10039!",db.queryForObject("SELECT password FROM t_user WHERE username=?",String.class,username)));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM t_class_student WHERE student_id=?",Integer.class,STUDENT));
    }

    @Test void joinedStudentSeesPublishedCourseOnlyAfterTeacherPublishesIt() {
        assertFalse(courses.listMyCourses(student(),1,20).getRecords().stream().anyMatch(c -> c.getId()==DRAFT));
        assertEquals("404",controller.selectById(DRAFT).getCode());
        db.update("UPDATE t_course SET status=1 WHERE id=?",DRAFT);
        assertTrue(courses.listMyCourses(student(),1,20).getRecords().stream().anyMatch(c -> c.getId()==DRAFT));
        assertEquals("200",controller.selectById(DRAFT).getCode());
    }

    @Test void inactiveClassCannotKeepCourseInStudentsJoinedList() {
        db.update("UPDATE t_course SET status=1 WHERE id=?",DRAFT);
        db.update("UPDATE t_teaching_class SET status=0 WHERE id=1003932");
        assertFalse(permissions.studentCourseIds(STUDENT).contains(DRAFT));
        assertFalse(courses.listMyCourses(student(),1,20).getRecords().stream().anyMatch(c -> c.getId()==DRAFT));
    }

    @Test void repeatedSeedPreservesTeacherEditsAndPublishedState() throws Exception {
        db.update("UPDATE t_course SET status=1,title='教师已编辑的录制课' WHERE id=?",DRAFT);
        db.update("UPDATE t_lesson SET content='教师已编辑的正文' WHERE id=1003953");
        seed();seed();
        assertEquals(1,db.queryForObject("SELECT status FROM t_course WHERE id=?",Integer.class,DRAFT));
        assertEquals("教师已编辑的录制课",db.queryForObject("SELECT title FROM t_course WHERE id=?",String.class,DRAFT));
        assertEquals("教师已编辑的正文",db.queryForObject("SELECT content FROM t_lesson WHERE id=1003953",String.class));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM t_class_student WHERE student_id=?",Integer.class,STUDENT));
    }

    @Test void teacherCanReadOwnDraftBeforePublication() {
        var teacher=new AuthUtils.LoginUser();teacher.setUserId(1003901);teacher.setRoleCode("teacher");UserContext.set(teacher);
        assertEquals("200",controller.selectById(DRAFT).getCode());
    }

    void seed() throws Exception {
        try (Connection connection=db.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("contest-demo-seed.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("contest-recording-seed.sql"));
        }
    }
    AuthUtils.LoginUser student() {
        var user=new AuthUtils.LoginUser();user.setUserId(STUDENT);user.setRoleCode("student");return user;
    }
}
