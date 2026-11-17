package com.cupk.aiacademy.model;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RuntimeModelProperties.class)
public class RuntimeModelConfiguration {
    @Bean @Primary
    public OpenAiChatModel generationChatModel(RuntimeModelProperties properties) { return create(properties.getGeneration()); }
    @Bean
    public OpenAiChatModel planningChatModel(RuntimeModelProperties properties) { return create(properties.getPlanning()); }

    private OpenAiChatModel create(RuntimeModelProperties.Endpoint endpoint) {
        // Invalid/missing settings may start the application; the guard rejects every call before I/O.
        boolean ready = "CONFIGURED_UNVERIFIED".equals(RuntimeModelPolicy.status(endpoint));
        String base = ready ? endpoint.getBaseUrl().replaceAll("/+$", "") : "http://127.0.0.1:1";
        String path = base.endsWith("/v1") ? "/chat/completions" : "/v1/chat/completions";
        var duration = Duration.ofSeconds(Math.max(1, endpoint.getTimeoutSeconds()));
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(duration).build());
        factory.setReadTimeout(duration);
        var api = new OpenAiApi(base, ready ? endpoint.getApiKey() : "NOT_CONFIGURED", path, "/v1/embeddings",
                RestClient.builder().requestFactory(factory)
                        .requestInterceptor(new GraphExtractionRequestInterceptor()),
                WebClient.builder(), new DefaultResponseErrorHandler());
        return new GuardedChatModel(api, endpoint);
    }
}
