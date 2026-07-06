package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DashboardControllerIntegrationTest {
    private static final int ADMIN_ID = 1;
    private static final int TEACHER_ID = 2;
    private static final int OTHER_TEACHER_ID = 4;
    private static final int STUDENT_ID = 3;

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedData() {
        UserContext.clear();
        clearTables();
        seedUsersAndClasses();
        seedStudentTodos();
        seedTeacherSubmissions();
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void studentSummaryReturnsOnlyOpenUnfinishedHomeworkAndExams() {
        loginAs(STUDENT_ID, "student");

        Map<String, Object> data = summaryData();

        List<Map<String, Object>> pendingHomework = rows(data, "pendingHomework");
        assertEquals(1, pendingHomework.size());
        assertEquals(101, pendingHomework.get(0).get("id"));
        assertEquals("待完成作业", pendingHomework.get(0).get("title"));
        assertTrue(pendingHomework.get(0).containsKey("deadline"));

        List<Map<String, Object>> upcomingExams = rows(data, "upcomingExams");
        assertEquals(1, upcomingExams.size());
        assertEquals(201, upcomingExams.get(0).get("id"));
        assertEquals("待参加考试", upcomingExams.get(0).get("title"));
        assertTrue(upcomingExams.get(0).containsKey("startTime"));
        assertTrue(upcomingExams.get(0).containsKey("endTime"));
    }

    @Test
    void teacherSummaryCountsOwnClassesPendingGradingAndLowScores() {
        loginAs(TEACHER_ID, "teacher");

        Map<String, Object> data = summaryData();

        assertEquals(2L, data.get("classCount"));
        assertEquals(1L, data.get("activeClassCount"));
        assertEquals(1L, data.get("pendingGradingCount"));
        assertEquals(1L, data.get("lowScoreCount"));

        List<Map<String, Object>> recentSubmissions = rows(data, "recentSubmissions");
        assertEquals(3, recentSubmissions.size());
        assertEquals("低分学生", recentSubmissions.get(0).get("studentName"));
        assertEquals("低分作业", recentSubmissions.get(0).get("homeworkTitle"));
        assertEquals(55, ((Number) recentSubmissions.get(0).get("score")).intValue());
        assertEquals(1, ((Number) recentSubmissions.get(0).get("gradeStatus")).intValue());
    }

    @Test
    void adminSummaryUsesGlobalTeachingData() {
        loginAs(ADMIN_ID, "admin");

        Map<String, Object> data = summaryData();

        assertEquals(3L, data.get("classCount"));
        assertEquals(2L, data.get("activeClassCount"));
        assertEquals(2L, data.get("pendingGradingCount"));
        assertEquals(1L, data.get("lowScoreCount"));
        assertEquals(4, rows(data, "recentSubmissions").size());
    }

    private void clearTables() {
        jdbcTemplate.update("DELETE FROM t_exam_answer");
        jdbcTemplate.update("DELETE FROM t_exam_record");
        jdbcTemplate.update("DELETE FROM t_exam_session");
        jdbcTemplate.update("DELETE FROM t_exam_paper_question");
        jdbcTemplate.update("DELETE FROM t_exam_paper");
        jdbcTemplate.update("DELETE FROM t_question");
        jdbcTemplate.update("DELETE FROM t_homework_submit");
        jdbcTemplate.update("DELETE FROM t_homework");
        jdbcTemplate.update("DELETE FROM t_class_student");
        jdbcTemplate.update("DELETE FROM t_teaching_class");
        jdbcTemplate.update("DELETE FROM t_course");
        jdbcTemplate.update("DELETE FROM t_course_category");
        jdbcTemplate.update("DELETE FROM t_user_role");
        jdbcTemplate.update("DELETE FROM t_user");
        jdbcTemplate.update("DELETE FROM t_role");
    }

    private void seedUsersAndClasses() {
        jdbcTemplate.update("INSERT INTO t_role(id, role_name, role_code, create_time) VALUES (1, '管理员', 'admin', NOW())");
        jdbcTemplate.update("INSERT INTO t_role(id, role_name, role_code, create_time) VALUES (2, '教师', 'teacher', NOW())");
        jdbcTemplate.update("INSERT INTO t_role(id, role_name, role_code, create_time) VALUES (3, '学生', 'student', NOW())");
        jdbcTemplate.update("INSERT INTO t_user(id, username, password, nickname, status, create_time, update_time) VALUES (?, 'admin', 'pw', '管理员', 1, NOW(), NOW())", ADMIN_ID);
        jdbcTemplate.update("INSERT INTO t_user(id, username, password, nickname, status, create_time, update_time) VALUES (?, 'teacher', 'pw', '测试教师', 1, NOW(), NOW())", TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_user(id, username, password, nickname, status, create_time, update_time) VALUES (?, 'student', 'pw', '低分学生', 1, NOW(), NOW())", STUDENT_ID);
        jdbcTemplate.update("INSERT INTO t_user(id, username, password, nickname, status, create_time, update_time) VALUES (?, 'teacher2', 'pw', '其他教师', 1, NOW(), NOW())", OTHER_TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_user_role(id, user_id, role_id) VALUES (1, ?, 1)", ADMIN_ID);
        jdbcTemplate.update("INSERT INTO t_user_role(id, user_id, role_id) VALUES (2, ?, 2)", TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_user_role(id, user_id, role_id) VALUES (3, ?, 3)", STUDENT_ID);
        jdbcTemplate.update("INSERT INTO t_user_role(id, user_id, role_id) VALUES (4, ?, 2)", OTHER_TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_course_category(id, name, description, sort_order, create_time, update_time) VALUES (1, '后端开发', '后端课程', 1, NOW(), NOW())");
        jdbcTemplate.update("INSERT INTO t_course(id, category_id, teacher_id, title, description, difficulty, status, created_at, updated_at) VALUES (1, 1, ?, 'Java', 'Java 基础', 'EASY', 1, NOW(), NOW())", TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_course(id, category_id, teacher_id, title, description, difficulty, status, created_at, updated_at) VALUES (2, 1, ?, 'Python', 'Python 基础', 'EASY', 1, NOW(), NOW())", OTHER_TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_teaching_class(id, course_id, teacher_id, class_name, semester, status, create_time, update_time) VALUES (11, 1, ?, 'Java 1 班', '2026', 1, NOW(), NOW())", TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_teaching_class(id, course_id, teacher_id, class_name, semester, status, create_time, update_time) VALUES (12, 1, ?, 'Java 2 班', '2026', 0, NOW(), NOW())", TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_teaching_class(id, course_id, teacher_id, class_name, semester, status, create_time, update_time) VALUES (13, 2, ?, 'Python 1 班', '2026', 1, NOW(), NOW())", OTHER_TEACHER_ID);
        jdbcTemplate.update("INSERT INTO t_class_student(id, class_id, student_id, join_time, status) VALUES (1, 11, ?, NOW(), 1)", STUDENT_ID);
    }

    private void seedStudentTodos() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO t_homework(id, class_id, teacher_id, title, content, full_score, start_time, deadline, status, create_time, update_time) VALUES (101, 11, ?, '待完成作业', 'content', 100, ?, ?, 1, NOW(), NOW())", TEACHER_ID, now.minusDays(1), now.plusDays(1));
        jdbcTemplate.update("INSERT INTO t_homework(id, class_id, teacher_id, title, content, full_score, start_time, deadline, status, create_time, update_time) VALUES (102, 11, ?, '已提交作业', 'content', 100, ?, ?, 1, NOW(), NOW())", TEACHER_ID, now.minusDays(1), now.plusDays(1));
        jdbcTemplate.update("INSERT INTO t_homework(id, class_id, teacher_id, title, content, full_score, start_time, deadline, status, create_time, update_time) VALUES (103, 11, ?, '已截止作业', 'content', 100, ?, ?, 1, NOW(), NOW())", TEACHER_ID, now.minusDays(3), now.minusDays(1));
        jdbcTemplate.update("INSERT INTO t_homework_submit(id, homework_id, student_id, submit_content, submit_time, score, grade_status, create_time, update_time) VALUES (301, 102, ?, 'done', ?, 88, 1, NOW(), NOW())", STUDENT_ID, now.minusHours(3));

        jdbcTemplate.update("INSERT INTO t_exam_session(id, class_id, paper_id, teacher_id, title, start_time, end_time, duration, status, create_time, update_time) VALUES (201, 11, 1, ?, '待参加考试', ?, ?, 60, 1, NOW(), NOW())", TEACHER_ID, now.minusMinutes(5), now.plusHours(2));
        jdbcTemplate.update("INSERT INTO t_exam_session(id, class_id, paper_id, teacher_id, title, start_time, end_time, duration, status, create_time, update_time) VALUES (202, 11, 1, ?, '已完成考试', ?, ?, 60, 1, NOW(), NOW())", TEACHER_ID, now.minusMinutes(5), now.plusHours(2));
        jdbcTemplate.update("INSERT INTO t_exam_session(id, class_id, paper_id, teacher_id, title, start_time, end_time, duration, status, create_time, update_time) VALUES (203, 11, 1, ?, '已结束考试', ?, ?, 60, 3, NOW(), NOW())", TEACHER_ID, now.minusDays(2), now.minusDays(1));
        jdbcTemplate.update("INSERT INTO t_exam_record(id, exam_session_id, student_id, candidate_id, start_time, submit_time, total_score, status, create_time, update_time) VALUES (401, 202, ?, '3', ?, ?, 95, 2, NOW(), NOW())", STUDENT_ID, now.minusMinutes(4), now.minusMinutes(2));
    }

    private void seedTeacherSubmissions() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO t_homework(id, class_id, teacher_id, title, content, full_score, start_time, deadline, status, create_time, update_time) VALUES (104, 11, ?, '待批改作业', 'content', 100, ?, ?, 1, NOW(), NOW())", TEACHER_ID, now.minusDays(2), now.plusDays(2));
        jdbcTemplate.update("INSERT INTO t_homework(id, class_id, teacher_id, title, content, full_score, start_time, deadline, status, create_time, update_time) VALUES (106, 11, ?, '低分作业', 'content', 100, ?, ?, 1, NOW(), NOW())", TEACHER_ID, now.minusDays(2), now.plusDays(2));
        jdbcTemplate.update("INSERT INTO t_homework(id, class_id, teacher_id, title, content, full_score, start_time, deadline, status, create_time, update_time) VALUES (105, 13, ?, '其他教师作业', 'content', 100, ?, ?, 1, NOW(), NOW())", OTHER_TEACHER_ID, now.minusDays(2), now.plusDays(2));
        jdbcTemplate.update("INSERT INTO t_homework_submit(id, homework_id, student_id, submit_content, submit_time, score, grade_status, create_time, update_time) VALUES (302, 104, ?, 'pending grade', ?, NULL, 0, NOW(), NOW())", STUDENT_ID, now.minusHours(2));
        jdbcTemplate.update("INSERT INTO t_homework_submit(id, homework_id, student_id, submit_content, submit_time, score, grade_status, create_time, update_time) VALUES (303, 106, ?, 'low score', ?, 55, 1, NOW(), NOW())", STUDENT_ID, now.minusHours(1));
        jdbcTemplate.update("INSERT INTO t_homework_submit(id, homework_id, student_id, submit_content, submit_time, score, grade_status, create_time, update_time) VALUES (304, 105, ?, 'other pending', ?, NULL, 0, NOW(), NOW())", STUDENT_ID, now.minusMinutes(30));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summaryData() {
        Result result = dashboardController.summary();
        assertEquals("200", result.getCode());
        return (Map<String, Object>) result.getData();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> data, String key) {
        return (List<Map<String, Object>>) data.get(key);
    }

    private void loginAs(Integer userId, String roleCode) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode);
        user.setRoleCode(roleCode);
        UserContext.set(user);
    }
}
