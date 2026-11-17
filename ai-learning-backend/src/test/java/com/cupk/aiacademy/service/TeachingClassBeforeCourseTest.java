package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.controller.ClassStudentController;
import com.cupk.aiacademy.controller.CourseController;
import com.cupk.aiacademy.controller.TeachingClassController;
import com.cupk.aiacademy.dto.ClassStudentDto;
import com.cupk.aiacademy.dto.CourseDto;
import com.cupk.aiacademy.dto.TeachingClassDto;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:class-before-course;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:contest-demo-schema.sql"
})
class TeachingClassBeforeCourseTest {
    @Autowired JdbcTemplate db;
    @Autowired TeachingClassController classes;
    @Autowired ClassStudentController members;
    @Autowired CourseController courses;
    @Autowired CourseResourceService courseResources;
    @Autowired Validator validator;

    @BeforeEach void setup() throws Exception {
        try (Connection connection = db.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("contest-demo-seed.sql"));
        }
        db.update("DELETE FROM t_class_student");
        db.update("DELETE FROM t_teaching_class");
        db.update("DELETE FROM t_course");
        UserContext.set(actor(2, "teacher"));
    }

    @AfterEach void cleanup() {
        UserContext.clear();
    }

    @Test void classRequestAcceptsPendingCourseAndRejectsNonPositiveCourse() {
        assertTrue(validator.validate(classRequest(null)).isEmpty());
        assertFalse(validator.validate(classRequest(0)).isEmpty());
    }

    @Test void teacherCanCreateClassAndEnrollStudentBeforeCreatingCourse() {
        int classId = createPendingClass();
        enroll(classId);
        assertNull(db.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=?", Integer.class, classId));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM t_class_student WHERE class_id=? AND student_id=3", Integer.class, classId));
        assertTrue(courseResources.listMyCourses(actor(3, "student"), 1, 20).getRecords().isEmpty());
    }

    @Test void newCourseCanBeAssignedToExistingClassAndPublishedWithoutReenrollingStudent() {
        int classId = createPendingClass();
        enroll(classId);
        int courseId = createDraftCourse();
        assertEquals("200", classes.update(classId, classRequest(courseId)).getCode());
        assertTrue(courseResources.listMyCourses(actor(3, "student"), 1, 20).getRecords().isEmpty());
        assertEquals("200", courses.enable(courseId).getCode());
        assertEquals(courseId, courseResources.listMyCourses(actor(3, "student"), 1, 20).getRecords().getFirst().getId());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM t_class_student WHERE class_id=? AND student_id=3", Integer.class, classId));
    }

    @Test void assignmentRejectsMissingCourseAndPreservesPendingClass() {
        int classId = createPendingClass();
        assertEquals("404", classes.update(classId, classRequest(9000001)).getCode());
        assertNull(db.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=?", Integer.class, classId));
    }

    @Test void assignmentRejectsAnotherTeachersCourse() {
        int classId = createPendingClass();
        int courseId = createDraftCourse();
        db.update("UPDATE t_course SET teacher_id=77 WHERE id=?", courseId);
        assertEquals("400", classes.update(classId, classRequest(courseId)).getCode());
        assertNull(db.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=?", Integer.class, classId));
    }

    @Test void returningClassToPendingPreservesEnrolledStudent() {
        int classId = createPendingClass();
        enroll(classId);
        int courseId = createDraftCourse();
        assertEquals("200", classes.update(classId, classRequest(courseId)).getCode());
        assertEquals("200", courses.enable(courseId).getCode());
        assertEquals("200", classes.update(classId, classRequest(null)).getCode());
        assertNull(db.queryForObject("SELECT course_id FROM t_teaching_class WHERE id=?", Integer.class, classId));
        assertTrue(courseResources.listMyCourses(actor(3, "student"), 1, 20).getRecords().isEmpty());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM t_class_student WHERE class_id=? AND student_id=3", Integer.class, classId));
    }

    private int createPendingClass() {
        assertEquals("200", classes.add(classRequest(null)).getCode());
        return db.queryForObject("SELECT id FROM t_teaching_class WHERE class_name='人工智能一班'", Integer.class);
    }

    private void enroll(int classId) {
        ClassStudentDto member = new ClassStudentDto();
        member.setClassId(classId);
        member.setStudentId(3);
        member.setStatus(1);
        assertEquals("200", members.add(member).getCode());
    }

    private int createDraftCourse() {
        CourseDto course = new CourseDto();
        course.setTitle("人工智能基础");
        course.setCategoryId(5);
        course.setTeacherId(2);
        course.setDifficulty("beginner");
        course.setStatus(2);
        assertEquals("200", courses.add(course).getCode());
        return db.queryForObject("SELECT id FROM t_course WHERE title='人工智能基础'", Integer.class);
    }

    private TeachingClassDto classRequest(Integer courseId) {
        TeachingClassDto request = new TeachingClassDto();
        request.setCourseId(courseId);
        request.setTeacherId(2);
        request.setClassName("人工智能一班");
        request.setSemester("2026秋");
        request.setStatus(1);
        return request;
    }

    private AuthUtils.LoginUser actor(int id, String role) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(id);
        user.setRoleCode(role);
        return user;
    }
}
