package com.cupk.aiacademy.agent.core.llm;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SpringAiChatModelClient implements ChatModelClient {
    private final OpenAiChatModel chatModel;
    private final String provider;
    private final String configuredModel;
    private final Duration defaultTimeout;
    private final ExecutorService callExecutor;

    public SpringAiChatModelClient(OpenAiChatModel chatModel,
                                   @Value("${spring.ai.openai.base-url:}") String baseUrl,
                                   @Value("${spring.ai.openai.chat.options.model:}") String configuredModel,
                                   @Value("${agent.model.timeout-seconds:15}") long timeoutSeconds) {
        this.chatModel = chatModel;
        this.provider = providerFromBaseUrl(baseUrl);
        this.configuredModel = configuredModel == null ? "" : configuredModel.trim();
        this.defaultTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.callExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("agent-model-call-", 0).factory());
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String configuredModel() {
        return configuredModel;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return call(prompt, defaultTimeout);
    }

    @Override
    public ChatResponse call(Prompt prompt, Duration timeout) {
        Duration effectiveTimeout = positiveTimeout(timeout, defaultTimeout);
        Future<ChatResponse> future = callExecutor.submit(() -> chatModel.call(prompt));
        try {
            return future.get(Math.max(1, effectiveTimeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ChatModelTimeoutException(
                    "模型调用超过 " + effectiveTimeout.toSeconds() + " 秒，已取消", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ChatModelTimeoutException("模型调用被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("模型调用失败", cause);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return chatModel.stream(prompt);
    }

    @PreDestroy
    public void shutdown() {
        callExecutor.shutdownNow();
    }

    private Duration positiveTimeout(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value.compareTo(fallback) > 0 ? fallback : value;
    }

    private String providerFromBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "spring-ai-openai";
        }
        String normalized = baseUrl.toLowerCase();
        if (normalized.contains("deepseek")) {
            return "deepseek";
        }
        if (normalized.contains("openai")) {
            return "openai";
        }
        return "openai-compatible";
    }
}
