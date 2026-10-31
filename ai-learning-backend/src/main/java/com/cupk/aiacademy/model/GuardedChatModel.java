package com.cupk.aiacademy.model;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/** Keeps existing business injection points on one checked, compatible transport. */
public class GuardedChatModel extends OpenAiChatModel {
    private final RuntimeModelProperties.Endpoint endpoint;
    public GuardedChatModel(OpenAiApi api, RuntimeModelProperties.Endpoint endpoint) {
        super(api, OpenAiChatOptions.builder().model(endpoint.getModel().isBlank()
                        ? "MODEL_NOT_CONFIGURED" : endpoint.getModel()).build(),
                null, RetryTemplate.builder().maxAttempts(1).build());
        this.endpoint = endpoint;
    }
    private void check(Prompt prompt) {
        RuntimeModelPolicy.requireReady(endpoint, prompt.getOptions() == null ? null : prompt.getOptions().getModel());
    }
    @Override public ChatResponse call(Prompt prompt) {
        check(prompt);
        try { return super.call(prompt); } catch(RuntimeException e) { throw safeFailure(e); }
    }
    @Override public ChatResponse internalCall(Prompt prompt, ChatResponse previous) {
        check(prompt);
        try { return super.internalCall(prompt,previous); } catch(RuntimeException e) { throw safeFailure(e); }
    }
    @Override public Flux<ChatResponse> stream(Prompt prompt) {
        return deadline(Flux.defer(() -> { check(prompt); return super.stream(prompt); }));
    }
    @Override public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previous) {
        return deadline(Flux.defer(() -> { check(prompt); return super.internalStream(prompt,previous); }));
    }
    private Flux<ChatResponse> deadline(Flux<ChatResponse> response) {
        return response.takeUntilOther(Mono.delay(Duration.ofSeconds(Math.max(1,endpoint.getTimeoutSeconds())))
                        .flatMap(tick->Mono.error(new ModelInvocationException("MODEL_TIMEOUT"))))
                .onErrorMap(this::safeFailure);
    }
    private RuntimeException safeFailure(Throwable error) {
        if(error instanceof ModelConfigurationException config) return config;
        if(error instanceof ModelInvocationException invocation) return invocation;
        String code="MODEL_CALL_FAILED";
        if(error instanceof RestClientResponseException response) code="MODEL_HTTP_"+response.getStatusCode().value();
        else if(error instanceof WebClientResponseException response) code="MODEL_HTTP_"+response.getStatusCode().value();
        else if(error instanceof java.util.concurrent.TimeoutException) code="MODEL_TIMEOUT";
        return new ModelInvocationException(code);
    }
}
