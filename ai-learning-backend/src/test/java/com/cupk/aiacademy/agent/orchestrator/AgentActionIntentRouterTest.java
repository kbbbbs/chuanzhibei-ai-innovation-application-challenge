package com.cupk.aiacademy.agent.orchestrator;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentActionSpecRegistry;
import com.cupk.aiacademy.agent.action.InMemoryAgentPendingActionStore;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.homework.HomeworkCreatePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.pojo.AgentDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionIntentRouterTest {
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void routesLegacyPlanToolThroughSchemaPlanFactory() {
        loginAs(7);
        InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
        HomeworkCreatePlanTool tool = new HomeworkCreatePlanTool(pendingActionStore);
        AgentActionSpecRegistry registry = new AgentActionSpecRegistry();
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(tool), registry);
        AgentActionPlanFactory planFactory = new AgentActionPlanFactory(pendingActionStore);
        AgentActionIntentRouter router = new AgentActionIntentRouter(
                new AgentConfigurationParser(null),
                new AgentToolExecutor(List.of(tool)),
                null,
                catalog,
                planFactory
        );
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"homework_create_plan\"]");

        AgentDirectActionReply reply = router.buildDirectActionReply(
                agent,
                "发布作业，教学班ID 1，标题叫第一次作业"
        ).orElseThrow();

        assertTrue(reply.content().contains("指令模式：待确认"));
        assertNotNull(reply.pendingAction());
        assertEquals("homework.create", reply.pendingAction().actionCode());
        assertTrue(reply.pendingAction().parameters().containsKey("parameterSchema"));
        assertTrue(reply.pendingAction().parameters().containsKey("missingRequiredFields"));
    }

    private void loginAs(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setRoleCode("teacher");
        UserContext.set(user);
    }
}
