package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.course.TeachingClassCreateActionExecutor;
import com.cupk.aiacademy.agent.action.course.TeachingClassStatusActionExecutor;
import com.cupk.aiacademy.agent.action.course.TeachingClassUpdateActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.TeachingClassDto;
import com.cupk.aiacademy.dto.ClassStudentDto;
import com.cupk.aiacademy.mapper.*;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeachingClassMaintenanceTest {
    @Test void concurrentMembershipMoveRejectsAnUpdateUsingTheOldClass() {
        assertConcurrentMembershipMoveRejected(false);
    }

    @Test void concurrentMembershipMoveRejectsADeleteUsingTheOldClass() {
        assertConcurrentMembershipMoveRejected(true);
    }

    private void assertConcurrentMembershipMoveRejected(boolean deleting) {
        ClassStudentMapper members = mock(ClassStudentMapper.class);
        TeachingClassMapper classes = mock(TeachingClassMapper.class);
        PermissionService permissions = mock(PermissionService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClassStudent before = new ClassStudent(); before.setId(55); before.setClassId(12); before.setStudentId(21);
        ClassStudent after = new ClassStudent(); after.setId(55); after.setClassId(99); after.setStudentId(21);
        TeachingClass teachingClass = classroom();
        when(members.selectById(55)).thenReturn(before);
        when(members.selectForUpdate(55)).thenReturn(after);
        when(permissions.getTeachingClass(12)).thenReturn(teachingClass);
        when(classes.selectForUpdate(12)).thenReturn(teachingClass);
        ClassStudentController controller = new ClassStudentController();
        ReflectionTestUtils.setField(controller, "classStudentMapper", members);
        ReflectionTestUtils.setField(controller, "classMapper", classes);
        ReflectionTestUtils.setField(controller, "permissionService", permissions);
        ReflectionTestUtils.setField(controller, "jdbc", jdbc);
        ClassStudentDto request = new ClassStudentDto(); request.setClassId(12); request.setStudentId(21); request.setStatus(1);

        Result result = deleting ? controller.delete(55) : controller.update(55, request);
        assertEquals("409", result.getCode());
        verify(members, never()).updateById(any(ClassStudent.class));
        verify(members, never()).deleteById(55);
        verify(jdbc, never()).update(contains("workflow_version"), anyInt());
    }

    @Test void ordinaryClassEditingPreservesPublicationAndInvalidatesItsPreview() {
        TeachingClassMapper classes = mock(TeachingClassMapper.class);
        CourseMapper courses = mock(CourseMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PermissionService permissions = mock(PermissionService.class);
        TeachingClass existing = classroom();
        Course course = new Course(); course.setId(10); course.setTeacherId(8);
        when(classes.selectById(12)).thenReturn(existing);
        when(classes.selectForUpdate(12)).thenReturn(existing);
        when(classes.updateById(any(TeachingClass.class))).thenReturn(1);
        when(courses.selectById(10)).thenReturn(course);
        TeachingClassController controller = new TeachingClassController();
        ReflectionTestUtils.setField(controller, "classMapper", classes);
        ReflectionTestUtils.setField(controller, "courseMapper", courses);
        ReflectionTestUtils.setField(controller, "jdbc", jdbc);
        ReflectionTestUtils.setField(controller, "permissionService", permissions);
        ReflectionTestUtils.setField(controller, "authUtils", mock(AuthUtils.class));
        TeachingClassDto request = TeachingClassDto.fromEntity(existing); request.setClassName("更新后的班名");
        assertEquals("200", controller.update(12, request).getCode());
        org.mockito.ArgumentCaptor<TeachingClass> updated = org.mockito.ArgumentCaptor.forClass(TeachingClass.class);
        verify(classes).updateById(updated.capture());
        assertEquals(Boolean.TRUE, updated.getValue().getCourseOpen());
        verify(jdbc).update(contains("workflow_version"), eq(10));
    }

    @Test void aClassWithLearningHistoryCannotMoveToAnotherCourse() {
        TeachingClassMapper classes = mock(TeachingClassMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TeachingClass existing = classroom();
        when(classes.selectById(12)).thenReturn(existing);
        when(classes.selectForUpdate(12)).thenReturn(existing);
        when(jdbc.queryForObject(startsWith("SELECT COUNT"), eq(Long.class), eq(12))).thenReturn(1L);
        TeachingClassController controller = new TeachingClassController();
        ReflectionTestUtils.setField(controller, "classMapper", classes);
        ReflectionTestUtils.setField(controller, "jdbc", jdbc);
        ReflectionTestUtils.setField(controller, "permissionService", mock(PermissionService.class));
        TeachingClassDto request = TeachingClassDto.fromEntity(existing); request.setCourseId(20);

        assertEquals("409", controller.update(12, request).getCode());
        verify(classes, never()).updateById(any(TeachingClass.class));
    }

    @Test void teacherAgentCannotCreateModifyOrEnableAClass() {
        TeachingClassController maintenance = mock(TeachingClassController.class);
        TeachingClassMapper classes = mock(TeachingClassMapper.class);
        CourseMapper courses = mock(CourseMapper.class); UserMapper users = mock(UserMapper.class);
        AuthUtils.LoginUser teacher = new AuthUtils.LoginUser(); teacher.setUserId(8); teacher.setRoleCode("teacher");
        AgentActionExecutionContext context = new AgentActionExecutionContext(teacher, Map.of(), "");
        assertThrows(BusinessException.class, () -> new TeachingClassCreateActionExecutor(courses, users, maintenance).execute(context));
        assertThrows(BusinessException.class, () -> new TeachingClassUpdateActionExecutor(classes, courses, users, maintenance).execute(context));
        assertThrows(BusinessException.class, () -> new TeachingClassStatusActionExecutor(classes, maintenance).execute(context));
        verifyNoInteractions(maintenance, classes, courses, users);
    }

    @Test void administratorAgentUsesTheSameMaintenanceValidation() {
        TeachingClassController maintenance = mock(TeachingClassController.class);
        TeachingClassMapper classes = mock(TeachingClassMapper.class);
        TeachingClass existing = classroom();
        when(classes.selectById(12)).thenReturn(existing);
        when(classes.selectForUpdate(12)).thenReturn(existing);
        when(maintenance.update(eq(12), any())).thenReturn(Result.error("409", "教学班归属已更新，请刷新后重试"));
        AuthUtils.LoginUser admin = new AuthUtils.LoginUser(); admin.setUserId(1); admin.setRoleCode("admin");
        AgentActionExecutionContext context = new AgentActionExecutionContext(admin, Map.of("classId", 12, "status", 0), "");
        BusinessException failure = assertThrows(BusinessException.class,
                () -> new TeachingClassStatusActionExecutor(classes, maintenance).execute(context));
        assertTrue(failure.getMessage().contains("刷新"));
        verify(classes, never()).updateById(any(TeachingClass.class));
    }

    private TeachingClass classroom() {
        TeachingClass value = new TeachingClass(); value.setId(12); value.setCourseId(10); value.setTeacherId(8);
        value.setClassName("教学班"); value.setStatus(1); value.setCourseOpen(true);
        return value;
    }
}
