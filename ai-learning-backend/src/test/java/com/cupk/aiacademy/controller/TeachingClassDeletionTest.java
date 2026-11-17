package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeachingClassDeletionTest {
    private JdbcTemplate jdbc;
    private TeachingClassMapper classes;
    private TeachingClassController controller;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        // Deliberately no foreign keys: the local demo schema must not rely on cascades.
        jdbc.execute("CREATE TABLE t_course(id INT PRIMARY KEY,workflow_version BIGINT)");
        jdbc.execute("CREATE TABLE t_teaching_class(id INT PRIMARY KEY,course_id INT)");
        jdbc.execute("CREATE TABLE t_class_student(id INT PRIMARY KEY,class_id INT,student_id INT)");
        for (String table : java.util.List.of("t_learning_progress", "t_learning_record", "t_grade_record",
                "t_exam_session", "t_homework_session", "t_homework", "t_grade_import_log")) {
            jdbc.execute("CREATE TABLE " + table + "(id INT PRIMARY KEY,class_id INT)");
        }
        jdbc.update("INSERT INTO t_course VALUES(20,3)");
        jdbc.update("INSERT INTO t_teaching_class VALUES(12,10),(13,20)");
        jdbc.update("INSERT INTO t_class_student VALUES(1,12,21),(2,13,22)");
        classes = mock(TeachingClassMapper.class);
        when(classes.selectById(anyInt())).thenAnswer(call -> readClass(call.getArgument(0), false));
        when(classes.selectForUpdate(anyInt())).thenAnswer(call -> readClass(call.getArgument(0), true));
        when(classes.deleteById(anyInt())).thenAnswer(call ->
                jdbc.update("DELETE FROM t_teaching_class WHERE id=?", (Integer) call.getArgument(0)));
        controller = new TeachingClassController();
        ReflectionTestUtils.setField(controller, "classMapper", classes);
        ReflectionTestUtils.setField(controller, "permissionService", mock(PermissionService.class));
        ReflectionTestUtils.setField(controller, "jdbc", jdbc);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    @Test void administratorCanRemoveAnOrphanClassAndOnlyItsMembershipsWithoutForeignKeys() {
        assertEquals("200", transaction.execute(status -> controller.delete(12)).getCode());
        assertEquals(0, count("t_teaching_class", "id=12"));
        assertEquals(0, count("t_class_student", "class_id=12"));
        assertEquals(1, count("t_teaching_class", "id=13"));
        assertEquals(1, count("t_class_student", "class_id=13"));
        assertEquals(3L, jdbc.queryForObject("SELECT workflow_version FROM t_course WHERE id=20", Long.class));
    }

    @Test void ordinaryClassDeletionAlsoRemovesMembershipsAndInvalidatesPublicationPreview() {
        assertEquals("200", transaction.execute(status -> controller.delete(13)).getCode());
        assertEquals(0, count("t_class_student", "class_id=13"));
        assertEquals(4L, jdbc.queryForObject("SELECT workflow_version FROM t_course WHERE id=20", Long.class));
    }

    @Test void orphanClassesWithLearningHistoryRemainProtected() {
        jdbc.update("INSERT INTO t_learning_progress VALUES(1,12)");
        assertEquals("409", transaction.execute(status -> controller.delete(12)).getCode());
        assertEquals(1, count("t_teaching_class", "id=12"));
        assertEquals(1, count("t_class_student", "class_id=12"));
        verify(classes, never()).deleteById(12);
    }

    @Test void membershipCleanupRollsBackWhenClassDeletionFails() {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("test failure")).when(classes).deleteById(12);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> transaction.execute(status -> controller.delete(12)));
        assertEquals(1, count("t_teaching_class", "id=12"));
        assertEquals(1, count("t_class_student", "class_id=12"));
    }

    private TeachingClass readClass(Integer id, boolean locked) {
        return jdbc.query("SELECT * FROM t_teaching_class WHERE id=?" + (locked ? " FOR UPDATE" : ""),
                (row, n) -> {
                    TeachingClass result = new TeachingClass();
                    result.setId(row.getInt("id")); result.setCourseId(row.getInt("course_id"));
                    return result;
                }, id).stream().findFirst().orElse(null);
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + condition, Integer.class);
    }
}
