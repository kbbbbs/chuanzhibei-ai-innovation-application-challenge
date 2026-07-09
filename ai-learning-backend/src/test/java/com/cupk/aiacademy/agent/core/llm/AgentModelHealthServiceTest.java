package com.cupk.aiacademy.agent.core.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentModelHealthServiceTest {

    @Mock
    private ChatModelClient chatModelClient;

    @Test
    void checkReturnsSanitizedDiagnosticsWhenModelCallFails() {
        when(chatModelClient.provider()).thenReturn("deepseek");
        when(chatModelClient.configuredModel()).thenReturn("deepseek-v4-flash");
        when(chatModelClient.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("401 invalid api key [REDACTED]"));

        AgentModelHealthResponse response = new AgentModelHealthService(chatModelClient).check();

        assertFalse(response.isHealthy());
        assertEquals("deepseek", response.getProvider());
        assertEquals("deepseek-v4-flash", response.getModel());
        assertNotNull(response.getDiagnostics());
        assertEquals("AUTHENTICATION", response.getDiagnostics().get("category"));
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = response.getDiagnostics();
        assertTrue(String.valueOf(diagnostics.get("rootMessage")).contains("sk-***"));
        assertFalse(String.valueOf(diagnostics.get("rootMessage")).contains("testSecret"));
    }
}
