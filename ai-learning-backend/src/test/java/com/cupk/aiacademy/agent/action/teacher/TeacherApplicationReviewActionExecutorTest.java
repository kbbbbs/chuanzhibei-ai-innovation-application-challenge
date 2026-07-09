package com.cupk.aiacademy.agent.action.teacher;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.teacher.TeacherApplicationReviewPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
import com.cupk.aiacademy.teacherapply.TeacherApplication;
import com.cupk.aiacademy.teacherapply.TeacherApplicationMapper;
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
class TeacherApplicationReviewActionExecutorTest {
    @Mock
    private TeacherApplicationMapper applicationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;

    @Test
    void approveApplicationCreatesTeacherUser() {
        TeacherApplication application = application(61);
        when(applicationMapper.selectById(61)).thenReturn(application);
        when(applicationMapper.updateById(any(TeacherApplication.class))).thenReturn(1);
        Role role = new Role();
        role.setId(2);
        role.setRoleCode("teacher");
        when(roleMapper.selectOne(any())).thenReturn(role);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(101);
            return 1;
        });
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);
        TeacherApplicationReviewActionExecutor executor = executor();

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("admin", 1),
                Map.of("applicationId", 61, "status", 1, "reviewComment", "资料完整"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("teacher.application.review", result.actionCode());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(TeacherApplicationReviewPlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(101, result.data().get("teacherUserId"));

        ArgumentCaptor<TeacherApplication> appCaptor = ArgumentCaptor.forClass(TeacherApplication.class);
        verify(applicationMapper).updateById(appCaptor.capture());
        assertEquals(1, appCaptor.getValue().getStatus());
        assertEquals(1, appCaptor.getValue().getReviewerId());
        assertEquals("资料完整", appCaptor.getValue().getReviewComment());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("teacher_apply", userCaptor.getValue().getUsername());
        assertEquals("申请教师", userCaptor.getValue().getNickname());
        verify(userRoleMapper).insert(any(UserRole.class));
    }

    @Test
    void rejectApplicationDoesNotCreateUser() {
        TeacherApplication application = application(61);
        when(applicationMapper.selectById(61)).thenReturn(application);
        when(applicationMapper.updateById(any(TeacherApplication.class))).thenReturn(1);
        TeacherApplicationReviewActionExecutor executor = executor();

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("admin", 1),
                Map.of("applicationId", 61, "status", 2, "reviewComment", "资料不足"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("教师申请已拒绝", result.message());
        assertEquals(2, result.data().get("status"));
        verify(userMapper, never()).insert(any(User.class));
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    void reviewRejectsNonAdmin() {
        TeacherApplicationReviewActionExecutor executor = executor();

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("teacher", 7),
                        Map.of("applicationId", 61, "status", 1),
                        ""
                )
        ));

        assertEquals("只有管理员可以审核教师申请", exception.getMessage());
        verify(applicationMapper, never()).updateById(any(TeacherApplication.class));
    }

    private TeacherApplicationReviewActionExecutor executor() {
        return new TeacherApplicationReviewActionExecutor(applicationMapper, userMapper, roleMapper, userRoleMapper);
    }

    private AuthUtils.LoginUser loginAs(String roleCode, Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode + userId);
        user.setRoleCode(roleCode);
        return user;
    }

    private TeacherApplication application(Integer id) {
        TeacherApplication application = new TeacherApplication();
        application.setId(id);
        application.setUsername("teacher_apply");
        application.setPassword("encoded-password");
        application.setNickname("申请教师");
        application.setEmail("teacher@example.com");
        application.setPhone("13800000000");
        application.setStatus(0);
        return application;
    }
}
