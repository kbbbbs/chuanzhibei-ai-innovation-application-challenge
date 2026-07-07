package com.cupk.aiacademy.agent.core.controller;

import com.cupk.aiacademy.agent.core.service.AgentChatService;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({AgentExceptionHandler.class, GlobalExceptionHandler.class})
class AgentExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentChatService agentChatService;

    @Test
    void agentBusinessExceptionReturnsResultShape() throws Exception {
        when(agentChatService.listMessages(1)).thenThrow(new BusinessException("Agent 会话不存在"));

        mockMvc.perform(get("/api/agent/sessions/1/messages"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("Agent 会话不存在"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void agentValidationExceptionReturnsResultShape() throws Exception {
        mockMvc.perform(post("/api/agent/sessions/1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("content 消息内容不能为空"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void agentSessionUpdateValidationReturnsResultShape() throws Exception {
        mockMvc.perform(put("/api/agent/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("title 会话标题不能为空"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }
}
