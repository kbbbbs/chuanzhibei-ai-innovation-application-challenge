package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.AgentPendingActionMapper;
import com.cupk.aiacademy.pojo.AgentPendingActionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPendingActionQueryServiceTest {
    @Mock
    private AgentPendingActionMapper pendingActionMapper;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void getCurrentUserPendingReturnsOwnedDetail() {
        loginAs(7);
        when(pendingActionMapper.selectById("action-1")).thenReturn(entity(7));
        AgentPendingActionQueryService service = newService();

        AgentPendingActionResponse response = service.getCurrentUserPending(" action-1 ");

        assertEquals("action-1", response.actionId());
        assertEquals("notice.create", response.actionCode());
        assertEquals("发布通知", response.actionName());
        assertEquals("MEDIUM", response.riskLevel());
        assertEquals("发布通知", response.parameters().get("rawRequest"));
    }

    @Test
    void getCurrentUserPendingRejectsOtherUser() {
        loginAs(7);
        when(pendingActionMapper.selectById("action-1")).thenReturn(entity(8));
        AgentPendingActionQueryService service = newService();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getCurrentUserPending("action-1"));

        assertEquals("无权限查看该待确认操作", exception.getMessage());
    }

    @Test
    void listCurrentUserPendingReturnsCurrentUserItems() {
        loginAs(7);
        when(pendingActionMapper.selectList(any())).thenReturn(List.of(entity(7)));
        AgentPendingActionQueryService service = newService();

        List<AgentPendingActionResponse> responses = service.listCurrentUserPending(200);

        assertEquals(1, responses.size());
        assertEquals("action-1", responses.get(0).actionId());
        assertEquals(null, responses.get(0).parameters());
    }

    private AgentPendingActionQueryService newService() {
        return new AgentPendingActionQueryService(
                pendingActionMapper,
                new ObjectMapper(),
                List.of(new TestActionExecutor())
        );
    }

    private AgentPendingActionEntity entity(Integer createdBy) {
        AgentPendingActionEntity entity = new AgentPendingActionEntity();
        entity.setActionId("action-1");
        entity.setActionCode("notice.create");
        entity.setCreatedBy(createdBy);
        entity.setParametersJson("{\"rawRequest\":\"发布通知\"}");
        entity.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return entity;
    }

    private void loginAs(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setRoleCode("teacher");
        UserContext.set(user);
    }

    private static class TestActionExecutor implements AgentActionExecutor {
        @Override
        public String actionCode() {
            return "notice.create";
        }

        @Override
        public String actionName() {
            return "发布通知";
        }

        @Override
        public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
            throw new UnsupportedOperationException();
        }
    }
}
