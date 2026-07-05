package com.cupk.aiacademy.agent.controller;

import com.cupk.aiacademy.agent.service.AgentDefinitionService;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.GlobalExceptionHandler;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.AgentCreateRequest;
import com.cupk.aiacademy.pojo.AgentDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent 定义管理接口测试，覆盖新增和状态切换接口的 Result 响应结构。
 */
@WebMvcTest(AgentDefinitionController.class)
@Import({AgentExceptionHandler.class, GlobalExceptionHandler.class})
class AgentDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentDefinitionService agentDefinitionService;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createDefinitionReturnsResultShape() throws Exception {
        loginAs("admin");
        when(agentDefinitionService.createDefinition(any(AgentCreateRequest.class))).thenReturn(agent());

        mockMvc.perform(post("/api/agent/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"java_tutor\",\"name\":\"Java 助教\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.code").value("java_tutor"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void createDefinitionRejectsNonAdmin() throws Exception {
        loginAs("student");

        mockMvc.perform(post("/api/agent/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"java_tutor\",\"name\":\"Java 助教\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"))
                .andExpect(jsonPath("$.msg").value("只有管理员可以维护 Agent 定义"));

        verify(agentDefinitionService, never()).createDefinition(any(AgentCreateRequest.class));
    }

    @Test
    void updateDefinitionRejectsNonAdmin() throws Exception {
        loginAs("teacher");

        mockMvc.perform(put("/api/agent/definitions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"java_tutor\",\"name\":\"Java 助教\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"))
                .andExpect(jsonPath("$.msg").value("只有管理员可以维护 Agent 定义"));

        verify(agentDefinitionService, never()).updateDefinition(eq(1), any(AgentCreateRequest.class));
    }

    @Test
    void updateStatusBusinessExceptionReturnsResultShape() throws Exception {
        loginAs("admin");
        when(agentDefinitionService.updateStatus(eq(1), eq(2)))
                .thenThrow(new BusinessException("status 只能为 0 或 1"));

        mockMvc.perform(put("/api/agent/definitions/1/status")
                        .param("status", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("status 只能为 0 或 1"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void updateStatusRejectsNonAdmin() throws Exception {
        loginAs("student");

        mockMvc.perform(put("/api/agent/definitions/1/status")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"))
                .andExpect(jsonPath("$.msg").value("只有管理员可以维护 Agent 定义"));

        verify(agentDefinitionService, never()).updateStatus(eq(1), eq(0));
    }

    private void loginAs(String roleCode) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(1);
        user.setUsername(roleCode + "_user");
        user.setRoleCode(roleCode);
        UserContext.set(user);
    }

    private AgentDefinition agent() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(1);
        agent.setCode("java_tutor");
        agent.setName("Java 助教");
        agent.setStatus(1);
        return agent;
    }
}
