package com.cupk.aiacademy.agent.action.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.ClassStudentRemovePlanTool;
import com.cupk.aiacademy.agent.tool.course.ClassStudentStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.pojo.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassStudentActionExecutorTest {
    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private UserMapper userMapper;

    @Test
    void classStudentAddInsertsStudentForOwnerTeacher() {
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 7));
        when(userMapper.selectById(30)).thenReturn(new User());
        when(classStudentMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(classStudentMapper.insert(any(ClassStudent.class))).thenAnswer(invocation -> {
            ClassStudent classStudent = invocation.getArgument(0);
            classStudent.setId(1001);
            return 1;
        });
        ClassStudentAddActionExecutor executor =
                new ClassStudentAddActionExecutor(classStudentMapper, teachingClassMapper, userMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("classId", "51", "studentId", "30"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("class.student.add", result.actionCode());
        assertEquals(1001, result.data().get("classStudentId"));

        ArgumentCaptor<ClassStudent> captor = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentMapper).insert(captor.capture());
        ClassStudent classStudent = captor.getValue();
        assertEquals(51, classStudent.getClassId());
        assertEquals(30, classStudent.getStudentId());
        assertEquals(1, classStudent.getStatus());
    }

    @Test
    void classStudentAddRejectsDuplicateMembership() {
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 7));
        when(userMapper.selectById(30)).thenReturn(new User());
        when(classStudentMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        ClassStudentAddActionExecutor executor =
                new ClassStudentAddActionExecutor(classStudentMapper, teachingClassMapper, userMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("classId", 51, "studentId", 30),
                        ""
                )
        ));

        assertEquals("该学生已加入该教学班", exception.getMessage());
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    void classStudentRemoveDeletesMembershipForOwnerTeacher() {
        when(classStudentMapper.selectById(1001)).thenReturn(classStudent(1001, 51, 30));
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 7));
        when(classStudentMapper.deleteById(1001)).thenReturn(1);
        ClassStudentRemoveActionExecutor executor =
                new ClassStudentRemoveActionExecutor(classStudentMapper, teachingClassMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("classStudentId", "1001", "reason", "转班"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("class.student.remove", result.actionCode());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(ClassStudentRemovePlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(1001, result.data().get("classStudentId"));
        verify(classStudentMapper).deleteById(1001);
    }

    @Test
    void classStudentStatusUpdatesMembershipStatusForOwnerTeacher() {
        when(classStudentMapper.selectById(1001)).thenReturn(classStudent(1001, 51, 30));
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 7));
        when(classStudentMapper.updateById(any(ClassStudent.class))).thenReturn(1);
        ClassStudentStatusActionExecutor executor =
                new ClassStudentStatusActionExecutor(classStudentMapper, teachingClassMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("teacher", 7),
                Map.of("classStudentId", "1001", "status", 2, "reason", "休学"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("class.student.status", result.actionCode());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(ClassStudentStatusPlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(1, result.data().get("oldStatus"));
        assertEquals(2, result.data().get("newStatus"));

        ArgumentCaptor<ClassStudent> captor = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentMapper).updateById(captor.capture());
        ClassStudent classStudent = captor.getValue();
        assertEquals(1001, classStudent.getId());
        assertEquals(2, classStudent.getStatus());
    }

    @Test
    void classStudentStatusRejectsInvalidStatus() {
        when(classStudentMapper.selectById(1001)).thenReturn(classStudent(1001, 51, 30));
        when(teachingClassMapper.selectById(51)).thenReturn(teachingClass(51, 7));
        ClassStudentStatusActionExecutor executor =
                new ClassStudentStatusActionExecutor(classStudentMapper, teachingClassMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("classStudentId", 1001, "status", 0),
                        ""
                )
        ));

        assertEquals("班级学生状态只能是 1 或 2", exception.getMessage());
        verify(classStudentMapper, never()).updateById(any(ClassStudent.class));
    }

    private AuthUtils.LoginUser loginAs(String roleCode, Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode + userId);
        user.setRoleCode(roleCode);
        return user;
    }

    private TeachingClass teachingClass(Integer id, Integer teacherId) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setCourseId(11);
        teachingClass.setTeacherId(teacherId);
        teachingClass.setClassName("Java 基础一班");
        teachingClass.setStatus(1);
        return teachingClass;
    }

    private ClassStudent classStudent(Integer id, Integer classId, Integer studentId) {
        ClassStudent classStudent = new ClassStudent();
        classStudent.setId(id);
        classStudent.setClassId(classId);
        classStudent.setStudentId(studentId);
        classStudent.setJoinTime(LocalDateTime.now());
        classStudent.setStatus(1);
        return classStudent;
    }
}
