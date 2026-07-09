package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.core.controller.AgentExceptionHandler;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentActionController.class)
@Import({AgentExceptionHandler.class, GlobalExceptionHandler.class})
class AgentActionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentActionExecutionService actionExecutionService;

    @MockitoBean
    private AgentPendingActionQueryService pendingActionQueryService;

    @Test
    void listPendingReturnsResultShape() throws Exception {
        when(pendingActionQueryService.listCurrentUserPending(20))
                .thenReturn(List.of(pendingActionResponse()));

        mockMvc.perform(get("/api/agent/actions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data[0].actionId").value("action-1"))
                .andExpect(jsonPath("$.data[0].actionCode").value("notice.create"));
    }

    @Test
    void getPendingReturnsResultShape() throws Exception {
        when(pendingActionQueryService.getCurrentUserPending("action-1"))
                .thenReturn(pendingActionResponse());

        mockMvc.perform(get("/api/agent/actions/pending/action-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.actionId").value("action-1"))
                .andExpect(jsonPath("$.data.actionName").value("发布通知"));
    }

    @Test
    void confirmReturnsResultShape() throws Exception {
        when(actionExecutionService.confirm(any(AgentActionConfirmRequest.class), anyString()))
                .thenReturn(new AgentActionExecutionResult(
                        "notice.create",
                        "发布通知",
                        true,
                        "通知已发布",
                        Map.of("noticeId", 55),
                        LocalDateTime.now()
                ));

        mockMvc.perform(post("/api/agent/actions/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionCode": "notice.create",
                                  "confirmed": true,
                                  "parameters": {
                                    "title": "作业提醒",
                                    "content": "明天提交第一章作业"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.msg").value("请求成功"))
                .andExpect(jsonPath("$.data.actionCode").value("notice.create"))
                .andExpect(jsonPath("$.data.data.noticeId").value(55))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void confirmBusinessExceptionReturnsResultShape() throws Exception {
        when(actionExecutionService.confirm(any(AgentActionConfirmRequest.class), anyString()))
                .thenThrow(new BusinessException("只有教师或管理员可以发布通知"));

        mockMvc.perform(post("/api/agent/actions/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionCode": "notice.create",
                                  "confirmed": true,
                                  "parameters": {
                                    "title": "作业提醒",
                                    "content": "明天提交第一章作业"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("只有教师或管理员可以发布通知"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    private AgentPendingActionResponse pendingActionResponse() {
        return new AgentPendingActionResponse(
                "action-1",
                "notice.create",
                "发布通知",
                "notice",
                "MEDIUM",
                true,
                null,
                null,
                7,
                Map.of("rawRequest", "发布通知"),
                List.of("会新增一条通知记录"),
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10),
                false
        );
    }
}
