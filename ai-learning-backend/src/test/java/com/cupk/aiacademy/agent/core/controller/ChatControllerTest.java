package com.cupk.aiacademy.agent.core.controller;

import com.cupk.aiacademy.agent.core.exception.AgentModelCallException;
import com.cupk.aiacademy.agent.core.llm.AgentModelDiagnostics;
import com.cupk.aiacademy.agent.core.service.AgentChatService;
import com.cupk.aiacademy.common.GlobalExceptionHandler;
import com.cupk.aiacademy.dto.AgentChatRequest;
import com.cupk.aiacademy.dto.AgentChatResponse;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent 对话入口测试，覆盖正式 API 的响应结构，避免回退为裸字符串响应。
 */
@WebMvcTest(ChatController.class)
@Import({AgentExceptionHandler.class, GlobalExceptionHandler.class})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentChatService agentChatService;

    @MockitoBean
    private OperationLogMapper operationLogMapper;

    @Test
    void chatOnceReturnsResultShape() throws Exception {
        when(agentChatService.chatOnce("你好")).thenReturn("你好，我是学习助教。");

        mockMvc.perform(post("/api/agent/chat-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.msg").value("请求成功"))
                .andExpect(jsonPath("$.data").value("你好，我是学习助教。"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void chatOnceModelFailureReturnsResultShape() throws Exception {
        when(agentChatService.chatOnce("你好"))
                .thenThrow(new AgentModelCallException("暂时无法回复，请重试。",
                        null,
                        null,
                        null,
                        AgentModelDiagnostics.from("deepseek",
                                "deepseek-v4-flash",
                                new RuntimeException("401 invalid api key [REDACTED]")),
                        null));

        mockMvc.perform(post("/api/agent/chat-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.msg").value("暂时无法回复，请重试。"))
                .andExpect(jsonPath("$.data.diagnostics.category").value("AUTHENTICATION"))
                .andExpect(jsonPath("$.data.diagnostics.retryable").value(false))
                .andExpect(jsonPath("$.data.diagnostics.provider").doesNotExist())
                .andExpect(jsonPath("$.data.diagnostics.model").doesNotExist())
                .andExpect(jsonPath("$.data.diagnostics.rootException").doesNotExist())
                .andExpect(jsonPath("$.data.diagnostics.rootMessage").doesNotExist())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void sessionChatReturnsResultShape() throws Exception {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(1);
        response.setUserMessageId(10);
        response.setAssistantMessageId(11);
        response.setContent("你好，我是学习助教。");
        response.setCreateTime(LocalDateTime.now());
        when(agentChatService.chat(eq(1), any(AgentChatRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/agent/sessions/1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.sessionId").value(1))
                .andExpect(jsonPath("$.data.userMessageId").value(10))
                .andExpect(jsonPath("$.data.assistantMessageId").value(11))
                .andExpect(jsonPath("$.data.content").value("你好，我是学习助教。"));
    }
}
