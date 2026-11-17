package com.cupk.aiacademy.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeModelPolicyTest {
    static RuntimeModelProperties.Endpoint endpoint(String url, String model) {
        var endpoint = new RuntimeModelProperties.Endpoint();
        endpoint.setBaseUrl(url);
        endpoint.setModel(model);
        endpoint.setApiKey("unit-test-secret");
        return endpoint;
    }

    @Test void missingConfigurationHasExplicitCodeWithoutSecret() throws Exception {
        var endpoint = new RuntimeModelProperties.Endpoint();
        endpoint.setApiKey("do-not-disclose-this-key");
        var error = assertThrows(ModelConfigurationException.class,
                () -> RuntimeModelPolicy.requireReady(endpoint, null));
        assertEquals("MODEL_NOT_CONFIGURED", error.code());
        assertFalse(error.getMessage().contains("do-not-disclose"));
        assertFalse(endpoint.toString().contains("do-not-disclose"));
        assertFalse(new ObjectMapper().writeValueAsString(endpoint).contains("do-not-disclose"));
    }

    @ParameterizedTest
    @ValueSource(strings={"gpt-4o", " GPT-5 ", "openai/gpt-4.1", "o1", "o3-mini", "o4-mini", "chatgpt-latest", "text-embedding-3-small"})
    void allowsGptAndOpenAiModelsIncludingRequestOverrides(String model) {
        var endpoint = endpoint("https://models.example.test", "qwen-test");
        assertDoesNotThrow(() -> RuntimeModelPolicy.requireReady(endpoint, model));
    }

    @ParameterizedTest
    @ValueSource(strings={"https://api.openai.com", "https://api.openai.com/v1", "https://models.openai.com", "https://api.openai.com./v1"})
    void allowsOfficialOpenAiEndpoint(String url) {
        assertDoesNotThrow(() -> RuntimeModelPolicy.requireReady(endpoint(url, "custom-alias"), null));
    }

    @ParameterizedTest
    @ValueSource(strings={"http://remote.example.test", "https://user:password@models.example.test", "https://models.example.test?api_key=hidden", "not-a-url", "https://models.example.test/#secret"})
    void rejectsInvalidEndpointWithoutEchoingIt(String url) {
        var error = assertThrows(ModelConfigurationException.class,
                () -> RuntimeModelPolicy.requireReady(endpoint(url, "qwen-test"), null));
        assertEquals("MODEL_ENDPOINT_INVALID", error.code());
        assertFalse(error.getMessage().contains(url));
    }

    @Test void compatibleProtocolAndLocalNonGptModelsAreAllowed() {
        assertDoesNotThrow(() -> RuntimeModelPolicy.requireReady(endpoint("http://127.0.0.1:8001/v1", "Qwen/test"), null));
        assertDoesNotThrow(() -> RuntimeModelPolicy.requireReady(endpoint("https://openai-compatible.example.test", "deepseek-test"), null));
        var disabled = endpoint("https://models.example.test", "qwen-test");
        disabled.setEnabled(false);
        assertEquals("MODEL_DISABLED", RuntimeModelPolicy.status(disabled));
    }

    @Test void httpAllowlistDoesNotRelaxUrlValidation() {
        var allowlist = "ollama";
        assertDoesNotThrow(() -> RuntimeModelPolicy.requireAllowedEndpoint("http://ollama:11434/v1", allowlist));
        for (String url : java.util.List.of("http://other:11434/v1", "http://user:pass@ollama:11434/v1",
                "http://ollama:11434/v1?secret=key", "http://ollama:11434/v1#fragment")) {
            assertThrows(ModelConfigurationException.class,
                    () -> RuntimeModelPolicy.requireAllowedEndpoint(url, allowlist));
        }
    }
}
