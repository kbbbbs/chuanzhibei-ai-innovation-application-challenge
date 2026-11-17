package com.cupk.aiacademy.agent.definition;

import com.cupk.aiacademy.agent.definition.controller.AgentDefinitionController;
import com.cupk.aiacademy.agent.definition.service.AgentDefinitionService;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AgentDefinitionAuthorizationTest {
    private final AgentDefinitionService definitions = mock(AgentDefinitionService.class);
    private final AgentDefinitionController controller = new AgentDefinitionController(definitions);

    @AfterEach void clear() { UserContext.clear(); }

    @Test void studentCannotReadInternalPromptsToolsOrBindings() {
        login("student");
        assertEquals("403", controller.listDefinitions(null, null).getCode());
        assertEquals("403", controller.getDefinition(1).getCode());
        verifyNoInteractions(definitions);
    }

    @Test void teacherCannotReadInternalConfiguration() {
        login("teacher");
        assertEquals("403", controller.getDefinition(1).getCode());
        verifyNoInteractions(definitions);
    }

    @Test void administratorCanReadConfiguration() {
        login("admin");
        assertEquals("200", controller.getDefinition(1).getCode());
        verify(definitions).getDefinition(1);
    }

    private void login(String role) {
        var user = new AuthUtils.LoginUser();
        user.setUserId(2);
        user.setRoleCode(role);
        UserContext.set(user);
    }
}
