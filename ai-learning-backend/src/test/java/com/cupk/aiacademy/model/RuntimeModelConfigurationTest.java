package com.cupk.aiacademy.model;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeModelConfigurationTest {
    private RuntimeModelProperties load(Map<String,Object> variables) throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove("systemEnvironment");
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().addFirst(new MapPropertySource("test-environment", variables));
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        return Binder.get(environment).bind("runtime.models", RuntimeModelProperties.class).get();
    }

    @Test void genericEnvironmentAliasesWorkAndPlanningInheritanceIsExplicit() throws Exception {
        var models = load(Map.of("BASE_URL","https://generic.example.test/v1", "API_KEY","generic-secret", "AI_OPENAI_MODEL","qwen-test"));
        assertEquals("https://generic.example.test/v1", models.getGeneration().getBaseUrl());
        assertEquals("generic-secret", models.getGeneration().getApiKey());
        assertEquals("qwen-test", models.getPlanning().getModel());
        assertEquals(models.getGeneration().getBaseUrl(), models.getPlanning().getBaseUrl());
        assertTrue(models.getEmbedding().getBaseUrl().isBlank());
        assertTrue(models.getOcr().getModel().isBlank());
        assertTrue(models.getOcr().getApiKey().isBlank());
    }

    @Test void perRoleVariablesWinWithoutBorrowingEmbeddingOrOcrCredentials() throws Exception {
        var models = load(Map.ofEntries(
                Map.entry("BASE_URL","https://generic.example.test"), Map.entry("API_KEY","generic-secret"),
                Map.entry("AI_OPENAI_BASE_URL","https://legacy.example.test"), Map.entry("AI_OPENAI_API_KEY","legacy-secret"),
                Map.entry("AI_GENERATION_BASE_URL","https://generation.example.test/v1"), Map.entry("AI_GENERATION_API_KEY","generation-secret"),
                Map.entry("AI_GENERATION_MODEL","qwen-generation"), Map.entry("AI_PLANNING_BASE_URL","https://planning.example.test"),
                Map.entry("AI_PLANNING_API_KEY","planning-secret"), Map.entry("AI_PLANNING_MODEL","qwen-planner"),
                Map.entry("RAG_EMBEDDING_ENABLED","true"), Map.entry("RAG_EMBEDDING_MODEL","bge-test"),
                Map.entry("RAG_EMBEDDING_BASE_URL","https://embedding.example.test"), Map.entry("RAG_EMBEDDING_API_KEY","embedding-secret")));
        assertEquals("https://generation.example.test/v1", models.getGeneration().getBaseUrl());
        assertEquals("generation-secret", models.getGeneration().getApiKey());
        assertEquals("qwen-planner", models.getPlanning().getModel());
        assertEquals("planning-secret", models.getPlanning().getApiKey());
        assertEquals("embedding-secret", models.getEmbedding().getApiKey());
        assertTrue(models.getOcr().getModel().isBlank());
    }

    @Test void noCredentialsStartsWithoutImplicitGptModelAndNoNetworkCall() {
        new ApplicationContextRunner().withUserConfiguration(RuntimeModelConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure());
            var model = context.getBean("generationChatModel", org.springframework.ai.openai.OpenAiChatModel.class);
            assertEquals("MODEL_NOT_CONFIGURED", assertThrows(ModelConfigurationException.class,
                    () -> model.call(new org.springframework.ai.chat.prompt.Prompt("hello"))).code());
            assertFalse(model.getDefaultOptions().getModel().toLowerCase().contains("gpt"));
        });
    }

    @Test void siliconFlowKeyIsSharedOnlyWhenExplicitlyConfiguredForThatProvider() throws Exception {
        var models=load(Map.of(
                "SILICONFLOW_API_KEY","test-siliconflow-key",
                "RAG_EMBEDDING_ENABLED","true",
                "RAG_EMBEDDING_BASE_URL","https://api.siliconflow.cn/v1",
                "RAG_EMBEDDING_MODEL","Qwen/Qwen3-VL-Embedding-8B",
                "OCR_BASE_URL","https://api.siliconflow.cn/v1",
                "OCR_MODEL","deepseek-ai/DeepSeek-OCR"));
        assertEquals("test-siliconflow-key",models.getEmbedding().getApiKey());
        assertEquals("test-siliconflow-key",models.getOcr().getApiKey());
        assertTrue(models.getGeneration().getApiKey().isBlank());
        assertTrue(models.getPlanning().getApiKey().isBlank());
    }

    @Test void siliconFlowRoleKeysOverrideSharedKeyAndMissingKeyRemainsUnconfigured() throws Exception {
        var models=load(Map.of(
                "SILICONFLOW_API_KEY","shared-test-key",
                "RAG_EMBEDDING_API_KEY","embedding-test-key",
                "OCR_API_KEY","ocr-test-key"));
        assertEquals("embedding-test-key",models.getEmbedding().getApiKey());
        assertEquals("ocr-test-key",models.getOcr().getApiKey());
        var missing=load(Map.of(
                "RAG_EMBEDDING_ENABLED","true",
                "RAG_EMBEDDING_BASE_URL","https://api.siliconflow.cn/v1",
                "RAG_EMBEDDING_MODEL","Qwen/Qwen3-VL-Embedding-8B",
                "OCR_BASE_URL","https://api.siliconflow.cn/v1",
                "OCR_MODEL","deepseek-ai/DeepSeek-OCR"));
        assertEquals("MODEL_NOT_CONFIGURED",RuntimeModelPolicy.status(missing.getEmbedding()));
        assertEquals("MODEL_NOT_CONFIGURED",RuntimeModelPolicy.status(missing.getOcr()));
    }

    @Test void acceptsUserConfiguredApiKeyAliasesAndPrefersCanonicalNames() throws Exception {
        var aliases=load(Map.of("RAG_EMBEDDING_APIKEY","embedding-alias-test-key", "OCR_APIKEY","ocr-alias-test-key"));
        assertEquals("embedding-alias-test-key",aliases.getEmbedding().getApiKey());
        assertEquals("ocr-alias-test-key",aliases.getOcr().getApiKey());
        var explicit=load(Map.of("RAG_EMBEDDING_APIKEY","old-embedding-key", "OCR_APIKEY","old-ocr-key",
                "RAG_EMBEDDING_API_KEY","canonical-embedding-key", "OCR_API_KEY","canonical-ocr-key"));
        assertEquals("canonical-embedding-key",explicit.getEmbedding().getApiKey());
        assertEquals("canonical-ocr-key",explicit.getOcr().getApiKey());
    }
}
