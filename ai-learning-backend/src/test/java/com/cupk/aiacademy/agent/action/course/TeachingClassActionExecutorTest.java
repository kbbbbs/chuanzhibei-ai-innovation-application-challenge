package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.TeachingClassStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeachingClassActionExecutorTest {
    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private UserMapper userMapper;

    @Test
    void teachingClassCreateInsertsClassForOwnerTeacher() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 7));
        when(userMapper.selectById(7)).thenReturn(new User());
        when(teachingClassMapper.insert(any(TeachingClass.class))).thenAnswer(invocation -> {
            TeachingClass teachingClass = invocation.getArgument(0);
            teachingClass.setId(51);
            return 1;
        });
        TeachingClassCreateActionExecutor executor =
                new TeachingClassCreateActionExecutor(teachingClassMapper, courseMapper, userMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "courseId", "11",
                        "className", "Java 基础一班",
                        "semester", "2026 春",
                        "description", "面向零基础学生"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("teaching.class.create", result.actionCode());
        assertEquals(51, result.data().get("classId"));

        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).insert(captor.capture());
        TeachingClass teachingClass = captor.getValue();
        assertEquals(11, teachingClass.getCourseId());
        assertEquals(7, teachingClass.getTeacherId());
        assertEquals("Java 基础一班", teachingClass.getClassName());
        assertEquals("2026 春", teachingClass.getSemester());
        assertEquals("面向零基础学生", teachingClass.getDescription());
        assertEquals(1, teachingClass.getStatus());
    }

    @Test
    void teachingClassCreateRejectsOtherTeacherCourse() {
        when(courseMapper.selectById(11)).thenReturn(course(11, 8));
        TeachingClassCreateActionExecutor executor =
                new TeachingClassCreateActionExecutor(teachingClassMapper, courseMapper, userMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("courseId", 11, "className", "Java 基础一班"),
                        ""
                )
        ));

        assertEquals("只能在当前登录教师负责的课程下创建教学班", exception.getMessage());
        verify(teachingClassMapper, never()).insert(any(TeachingClass.class));
    }

    @Test
    void teachingClassUpdateUpdatesAllowedFieldsForOwnerTeacher() {
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 11, 7));
        when(courseMapper.selectById(12)).thenReturn(course(12, 7));
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);
        TeachingClassUpdateActionExecutor executor =
                new TeachingClassUpdateActionExecutor(teachingClassMapper, courseMapper, userMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of(
                        "classId", "51",
                        "courseId", "12",
                        "className", "Java 进阶一班",
                        "semester", "2026 秋"
                ),
                ""
        ));

        assertTrue(result.success());
        assertEquals("teaching.class.update", result.actionCode());
        assertEquals("Java 基础一班", result.data().get("oldClassName"));
        assertEquals("Java 进阶一班", result.data().get("newClassName"));

        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).updateById(captor.capture());
        TeachingClass teachingClass = captor.getValue();
        assertEquals(51, teachingClass.getId());
        assertEquals(12, teachingClass.getCourseId());
        assertEquals(7, teachingClass.getTeacherId());
        assertEquals("Java 进阶一班", teachingClass.getClassName());
        assertEquals("2026 秋", teachingClass.getSemester());
        assertEquals(1, teachingClass.getStatus());
    }

    @Test
    void teachingClassStatusUpdatesStatusForOwnerTeacher() {
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 11, 7));
        when(teachingClassMapper.updateById(any(TeachingClass.class))).thenReturn(1);
        TeachingClassStatusActionExecutor executor = new TeachingClassStatusActionExecutor(teachingClassMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("classId", "51", "status", 0, "reason", "学期结束"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("teaching.class.status", result.actionCode());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(TeachingClassStatusPlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(1, result.data().get("oldStatus"));
        assertEquals(0, result.data().get("newStatus"));

        ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(teachingClassMapper).updateById(captor.capture());
        TeachingClass teachingClass = captor.getValue();
        assertEquals(51, teachingClass.getId());
        assertEquals(0, teachingClass.getStatus());
    }

    @Test
    void teachingClassStatusRejectsInvalidStatus() {
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 11, 7));
        TeachingClassStatusActionExecutor executor = new TeachingClassStatusActionExecutor(teachingClassMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("classId", 51, "status", 2),
                        ""
                )
        ));

        assertEquals("教学班状态只能是 0 或 1", exception.getMessage());
        verify(teachingClassMapper, never()).updateById(any(TeachingClass.class));
    }

    private AuthUtils.LoginUser loginAs(String roleCode, Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode + userId);
        user.setRoleCode(roleCode);
        return user;
    }

    private Course course(Integer id, Integer teacherId) {
        Course course = new Course();
        course.setId(id);
        course.setTeacherId(teacherId);
        course.setTitle("Java 基础");
        course.setStatus(1);
        return course;
    }

    private TeachingClass teachingClass(Integer id, Integer courseId, Integer teacherId) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setCourseId(courseId);
        teachingClass.setTeacherId(teacherId);
        teachingClass.setClassName("Java 基础一班");
        teachingClass.setSemester("2026 春");
        teachingClass.setDescription("面向零基础学生");
        teachingClass.setStatus(1);
        return teachingClass;
    }
}
