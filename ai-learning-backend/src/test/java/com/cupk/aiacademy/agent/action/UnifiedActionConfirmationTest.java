package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.action.course.ChapterCreateActionExecutor;
import com.cupk.aiacademy.agent.action.course.ClassStudentAddActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedActionConfirmationTest {
    @AfterEach
    void clear() { UserContext.clear(); }

    private void login(String role) {
        var actor = new AuthUtils.LoginUser();
        actor.setUserId(2);
        actor.setRoleCode(role);
        UserContext.set(actor);
    }

    private AgentActionExecutor executor() {
        var executor = mock(AgentActionExecutor.class);
        when(executor.actionCode()).thenReturn("chapter.create");
        when(executor.riskLevel()).thenReturn(AgentActionRiskLevel.LOW);
        when(executor.execute(any())).thenReturn(new AgentActionExecutionResult("chapter.create", "创建章节", true,
                "完成", Map.of("id", 11), LocalDateTime.now()));
        return executor;
    }

    private AgentActionExecutionService service(AgentPendingActionStore store, AgentActionExecutor executor) {
        return new AgentActionExecutionService(List.of(executor), mock(OperationLogMapper.class), new ObjectMapper(), store);
    }

    @Test
    void confirmationRequiresARealPreview() {
        login("teacher");
        var executor = executor();
        assertThrows(BusinessException.class, () -> service(new InMemoryAgentPendingActionStore(), executor)
                .confirm(new AgentActionConfirmRequest("chapter.create", true, Map.of("courseId", 129)), "local"));
        verify(executor, never()).execute(any());
    }

    @Test
    void previewParametersCannotBeOverridden() {
        login("teacher");
        var store = new InMemoryAgentPendingActionStore();
        var executor = executor();
        var pending = store.create("chapter.create", 2, Map.of("courseId", 129, "title", "原章节"), Duration.ofMinutes(5));
        var request = new AgentActionConfirmRequest("chapter.create", true,
                Map.of("courseId", 1, "title", "另一章节"), pending.actionId());
        assertThrows(BusinessException.class, () -> service(store, executor).confirm(request, "local"));
        verify(executor, never()).execute(any());
    }

    @Test
    void retryReturnsOriginalResultAndExecutesOnlyOnce() {
        login("teacher");
        var store = new InMemoryAgentPendingActionStore();
        var executor = executor();
        var pending = store.create("chapter.create", 2, Map.of("courseId", 129), Duration.ofMinutes(5));
        var service = service(store, executor);
        var request = new AgentActionConfirmRequest("chapter.create", true, Map.of(), pending.actionId());
        assertEquals(service.confirm(request, "local"), service.confirm(request, "local"));
        verify(executor, times(1)).execute(any());
    }

    @Test
    void teacherCannotManageClassMembersThroughOldExecutor() {
        login("teacher");
        var executor = new ClassStudentAddActionExecutor(mock(ClassStudentMapper.class), mock(TeachingClassMapper.class), mock(UserMapper.class));
        var error = assertThrows(BusinessException.class, () -> executor.execute(new AgentActionExecutionContext(
                UserContext.get(), Map.of("classId", 1, "studentId", 4), "local")));
        assertTrue(error.getMessage().contains("管理员"));
    }

    @Test
    void adminCannotModifyTeachingContentThroughOldExecutor() {
        login("admin");
        var executor = new ChapterCreateActionExecutor(mock(ChapterMapper.class), mock(CourseMapper.class), null);
        var error = assertThrows(BusinessException.class, () -> executor.execute(new AgentActionExecutionContext(
                UserContext.get(), Map.of("courseId", 129, "title", "章"), "local")));
        assertTrue(error.getMessage().contains("教师"));
    }
}
