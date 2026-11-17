package com.cupk.aiacademy.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

class GraphExtractionRequestInterceptorTest {
    @Test
    void installedSpringAiTransportDeliversTheGraphOptionToTheHttpBody() throws Exception {
        var captured = new AtomicReference<byte[]>();
        var marker = new AtomicReference<String>();
        var server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    captured.set(exchange.getRequestBody().readAllBytes());
                    marker.set(
                            exchange.getRequestHeaders()
                                    .getFirst(GraphExtractionRequestInterceptor.HEADER));
                    byte[] response =
                            "{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"deepseek-v4-pro\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        try {
            var properties = new RuntimeModelProperties();
            properties
                    .getGeneration()
                    .setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getGeneration().setApiKey("controlled-test-key");
            properties.getGeneration().setModel("deepseek-v4-pro");
            var model = new RuntimeModelConfiguration().generationChatModel(properties);
            var options =
                    org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .httpHeaders(
                                    java.util.Map.of(
                                            GraphExtractionRequestInterceptor.HEADER, "true"))
                            .build();
            model.call(new org.springframework.ai.chat.prompt.Prompt("extract", options));
            assertEquals(
                    "disabled",
                    new ObjectMapper()
                            .readTree(captured.get())
                            .path("thinking")
                            .path("type")
                            .asText());
            assertNull(marker.get());
            model.call(new org.springframework.ai.chat.prompt.Prompt("ordinary chat"));
            assertFalse(new ObjectMapper().readTree(captured.get()).has("thinking"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void onlyMarkedDeepSeekGraphRequestsDisableThinkingAndTheMarkerStaysLocal() throws Exception {
        var request = new MockClientHttpRequest();
        request.getHeaders().set(GraphExtractionRequestInterceptor.HEADER, "true");
        byte[] original =
                "{\"model\":\"deepseek-v4-pro\",\"messages\":[],\"stream\":false}"
                        .getBytes(StandardCharsets.UTF_8);
        var captured = new AtomicReference<byte[]>();
        new GraphExtractionRequestInterceptor()
                .intercept(
                        request,
                        original,
                        (outbound, body) -> {
                            captured.set(body);
                            assertFalse(
                                    outbound.getHeaders()
                                            .containsKey(GraphExtractionRequestInterceptor.HEADER));
                            assertEquals(body.length, outbound.getHeaders().getContentLength());
                            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                        });
        var json = new ObjectMapper().readTree(captured.get());
        assertEquals("disabled", json.path("thinking").path("type").asText());
        assertEquals("deepseek-v4-pro", json.path("model").asText());
        assertTrue(json.path("messages").isArray());
    }

    @Test
    void ordinaryChatAndOtherModelsRetainTheirRequestBodies() throws Exception {
        for (boolean marked : new boolean[] {false, true}) {
            var request = new MockClientHttpRequest();
            if (marked) request.getHeaders().set(GraphExtractionRequestInterceptor.HEADER, "true");
            byte[] original =
                    (marked ? "{\"model\":\"another-model\"}" : "{\"model\":\"deepseek-v4-pro\"}")
                            .getBytes(StandardCharsets.UTF_8);
            new GraphExtractionRequestInterceptor()
                    .intercept(
                            request,
                            original,
                            (outbound, body) -> {
                                assertArrayEquals(original, body);
                                assertFalse(
                                        outbound.getHeaders()
                                                .containsKey(
                                                        GraphExtractionRequestInterceptor.HEADER));
                                return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                            });
        }
    }
}
