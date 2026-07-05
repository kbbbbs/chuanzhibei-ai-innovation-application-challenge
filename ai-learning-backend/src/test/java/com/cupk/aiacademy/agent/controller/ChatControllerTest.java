package com.cupk.aiacademy.agent.controller;

import com.cupk.aiacademy.agent.exception.AgentModelCallException;
import com.cupk.aiacademy.agent.service.AgentChatService;
import com.cupk.aiacademy.common.GlobalExceptionHandler;
import com.cupk.aiacademy.dto.AgentChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
                .thenThrow(new AgentModelCallException("AI 回复失败，请稍后重试。", null, null, null));

        mockMvc.perform(post("/api/agent/chat-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.msg").value("AI 回复失败，请稍后重试。"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void streamChatReturnsTextEventStream() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(agentChatService.streamChat(eq(1), any(AgentChatRequest.class))).thenReturn(emitter);

        MvcResult mvcResult = mockMvc.perform(post("/api/agent/sessions/1/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.send(SseEmitter.event().name("message").data("ok"));
        emitter.complete();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("data:ok")));
    }
}
