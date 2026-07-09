package com.cupk.aiacademy.config;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.pojo.OperationLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsageLogInterceptorMvcTest.TestController.class)
@Import({
        AuthInterceptor.class,
        UsageLogInterceptor.class,
        WebMvcConfiguration.class,
        UsageLogInterceptorMvcTest.TestController.class
})
class UsageLogInterceptorMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthUtils authUtils;

    @MockitoBean
    private OperationLogMapper operationLogMapper;

    @BeforeEach
    void setUp() {
        reset(authUtils, operationLogMapper);
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(42);
        user.setUsername("teacher");
        user.setRoleCode("teacher");
        when(authUtils.requireLogin(any())).thenReturn(user);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void authenticatedCourseRequestWritesUsageLog() throws Exception {
        mockMvc.perform(get("/courses?pageNum=1&pageSize=10&token=secret-token")
                        .header("Authorization", "Bearer test-token")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());

        OperationLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(42);
        assertThat(log.getOperation()).isEqualTo("usage.request");
        assertThat(log.getMethod()).isEqualTo("GET");
        assertThat(log.getUrl()).isEqualTo("/courses");
        assertThat(log.getIp()).isEqualTo("203.0.113.10");
        assertThat(log.getCreateTime()).isNotNull();

        JsonNode params = objectMapper.readTree(log.getParams());
        assertThat(params.get("query").asText()).isEqualTo("pageNum=1&pageSize=10&token=[REDACTED]");
        assertThat(params.get("status").asInt()).isEqualTo(200);
        assertThat(params.get("durationMs").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(params.get("summary").asText()).isEqualTo("GET /courses");
        assertThat(params.get("userAgent").asText()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void operationLogEndpointIsNotLogged() throws Exception {
        mockMvc.perform(get("/operation-logs")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    void responseStatusFailureIsLogged() throws Exception {
        mockMvc.perform(get("/not-found-from-handler")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());

        OperationLog log = captor.getValue();
        JsonNode params = objectMapper.readTree(log.getParams());
        assertThat(log.getUserId()).isEqualTo(42);
        assertThat(log.getUrl()).isEqualTo("/not-found-from-handler");
        assertThat(params.get("status").asInt()).isEqualTo(404);
    }

    @Test
    void loggingFailureDoesNotChangeBusinessResponse() throws Exception {
        when(operationLogMapper.insert(any(OperationLog.class))).thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/courses")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @RestController
    public static class TestController {
        @GetMapping("/courses")
        Map<String, Object> courses() {
            return Map.of("ok", true);
        }

        @GetMapping("/operation-logs")
        Map<String, Object> operationLogs() {
            return Map.of("ok", true);
        }

        @GetMapping("/not-found-from-handler")
        Map<String, Object> notFound(HttpServletResponse response) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return Map.of("ok", false);
        }
    }
}
