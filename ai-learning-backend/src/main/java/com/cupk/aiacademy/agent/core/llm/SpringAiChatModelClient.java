package com.cupk.aiacademy.agent.core.llm;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import com.cupk.aiacademy.model.RuntimeModelProperties;
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
    private final OpenAiChatModel planningModel;
    private final Duration planningTimeout;
    private final String provider;
    private final String configuredModel;
    private final Duration defaultTimeout;
    private final ExecutorService callExecutor;

    public SpringAiChatModelClient(@Qualifier("generationChatModel") OpenAiChatModel chatModel,
                                   @Qualifier("planningChatModel") OpenAiChatModel planningModel,
                                   RuntimeModelProperties properties) {
        this.chatModel = chatModel;
        this.planningModel = planningModel;
        this.provider = providerFromBaseUrl(properties.getGeneration().getBaseUrl());
        this.configuredModel = properties.getGeneration().getModel();
        this.defaultTimeout = Duration.ofSeconds(Math.max(1, properties.getGeneration().getTimeoutSeconds()));
        this.planningTimeout = Duration.ofSeconds(Math.max(1, properties.getPlanning().getTimeoutSeconds()));
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
        return invoke(chatModel, prompt, effectiveTimeout);
    }

    @Override
    public ChatResponse callPlanning(Prompt prompt, Duration timeout) {
        return invoke(planningModel, prompt, positiveTimeout(timeout, planningTimeout));
    }

    private ChatResponse invoke(OpenAiChatModel model, Prompt prompt, Duration effectiveTimeout) {
        Future<ChatResponse> future = callExecutor.submit(() -> model.call(prompt));
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
            return "not-configured";
        }
        String host;
        try { host = java.net.URI.create(baseUrl).getHost(); }
        catch (IllegalArgumentException e) { return "not-configured"; }
        if (host == null) return "not-configured";
        String normalized = host.toLowerCase(java.util.Locale.ROOT).replaceAll("\\.+$", "");
        if (normalized.equals("deepseek.com") || normalized.endsWith(".deepseek.com")) {
            return "deepseek";
        }
        if (normalized.equals("openai.com") || normalized.endsWith(".openai.com")) {
            return "openai";
        }
        return "openai-compatible";
    }
}
