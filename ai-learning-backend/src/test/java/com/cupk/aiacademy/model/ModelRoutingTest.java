package com.cupk.aiacademy.model;

import com.cupk.aiacademy.agent.core.llm.SpringAiChatModelClient;
import com.cupk.aiacademy.agent.planner.AgentPlannerProperties;
import com.cupk.aiacademy.agent.planner.PlannerModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ModelRoutingTest {
    HttpServer server;
    String base;
    final CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<>();
    SpringAiChatModelClient client;
    RuntimeModelProperties settings;

    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            String body=new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestURI().getPath()+" "+exchange.getRequestHeaders().getFirst("Authorization")+" "+body);
            boolean streaming=new ObjectMapper().readTree(body).path("stream").asBoolean();
            String output=streaming ? "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n"
                    : "{\"id\":\"test\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
            exchange.getResponseHeaders().set("Content-Type",streaming?"text/event-stream":"application/json");
            byte[] bytes=output.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); base="http://127.0.0.1:"+server.getAddress().getPort();
        settings=new RuntimeModelProperties();
        settings.setGeneration(RuntimeModelPolicyTest.endpoint(base+"/generation/v1", "qwen-generation"));
        settings.setPlanning(RuntimeModelPolicyTest.endpoint(base+"/planning", "qwen-planner"));
        settings.getPlanning().setApiKey("planner-only-key");
        var configuration=new RuntimeModelConfiguration();
        client=new SpringAiChatModelClient(configuration.generationChatModel(settings), configuration.planningChatModel(settings), settings);
    }
    @AfterEach void cleanup() { if(client!=null)client.shutdown(); if(server!=null)server.stop(0); }

    @Test void generationAndPlanningUseDifferentEndpointsKeysAndModels() {
        assertEquals("ok",client.call(new Prompt("generation")).getResult().getOutput().getContent());
        var plannerProperties=new AgentPlannerProperties();
        assertEquals("ok",new PlannerModelClient(client,plannerProperties).call("plan"));
        assertEquals(2,requests.size());
        assertTrue(requests.get(0).startsWith("/generation/v1/chat/completions Bearer unit-test-secret"));
        assertTrue(requests.get(0).contains("qwen-generation"));
        assertTrue(requests.get(1).startsWith("/planning/v1/chat/completions Bearer planner-only-key"));
        assertTrue(requests.get(1).contains("qwen-planner"));
    }

    @Test void streamWorksAgainstCompatibleProtocolAndBlocksGptOverrideWithoutRequest() {
        assertFalse(client.stream(new Prompt("stream")).collectList().block(Duration.ofSeconds(5)).isEmpty());
        int previous=requests.size();
        var prompt=new Prompt("blocked", OpenAiChatOptions.builder().model("gpt-4o").build());
        assertThrows(ModelConfigurationException.class,()->client.call(prompt));
        assertThrows(ModelConfigurationException.class,()->client.stream(prompt).blockLast(Duration.ofSeconds(5)));
        assertEquals(previous,requests.size());
    }

    @Test void unavailablePlanningDoesNotFallBackToGeneration() {
        settings.getPlanning().setModel("");
        var configuration=new RuntimeModelConfiguration();
        var blocked=configuration.planningChatModel(settings);
        assertEquals("MODEL_NOT_CONFIGURED",assertThrows(ModelConfigurationException.class,()->blocked.call(new Prompt("plan"))).code());
        assertTrue(requests.isEmpty());
    }

    @Test void providerDiagnosticUsesHostnameRatherThanProtocolName() {
        settings.setGeneration(RuntimeModelPolicyTest.endpoint("https://openai-compatible.example.test", "qwen-test"));
        var configuration=new RuntimeModelConfiguration();
        var diagnosticClient=new SpringAiChatModelClient(configuration.generationChatModel(settings),
                configuration.planningChatModel(settings),settings);
        try { assertEquals("openai-compatible",diagnosticClient.provider()); }
        finally { diagnosticClient.shutdown(); }
    }

    @Test void publicInternalMethodsCannotBypassModelPolicy() {
        var guarded=new RuntimeModelConfiguration().generationChatModel(settings);
        var prompt=new Prompt("blocked",OpenAiChatOptions.builder().model("gpt-4o").build());
        assertThrows(ModelConfigurationException.class,()->guarded.internalCall(prompt,null));
        assertThrows(ModelConfigurationException.class,()->guarded.internalStream(prompt,null).blockLast(Duration.ofSeconds(3)));
        assertTrue(requests.isEmpty());
    }

    @Test void providerErrorBodyAndCredentialAreNotPropagated() {
        server.createContext("/failure/",exchange->{
            byte[] body="{\"error\":\"unit-test-secret at private-provider-url\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        var endpoint=RuntimeModelPolicyTest.endpoint(base+"/failure","qwen-test");
        settings.setGeneration(endpoint);
        var guarded=new RuntimeModelConfiguration().generationChatModel(settings);
        var error=assertThrows(RuntimeException.class,()->guarded.call(new Prompt("test")));
        assertFalse(error.toString().contains("unit-test-secret"));
        assertFalse(error.toString().contains("private-provider-url"));
        assertNull(error.getCause());
        var streamError=assertThrows(RuntimeException.class,()->guarded.stream(new Prompt("test")).blockLast(Duration.ofSeconds(3)));
        assertFalse(streamError.toString().contains("unit-test-secret"));
    }

    @Test void streamDeadlineLimitsTotalTimeEvenWithContinuingTokens() {
        server.createContext("/continuous/",exchange->{
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);
            try {
                for(int i=0;i<5;i++) {
                    String event="data: {\"id\":\"slow\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"a\"},\"finish_reason\":null}]}\n\n";
                    exchange.getResponseBody().write(event.getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();Thread.sleep(400);
                }
                exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            } catch(Exception ignored) { } finally {exchange.close();}
        });
        settings.setGeneration(RuntimeModelPolicyTest.endpoint(base+"/continuous","qwen-test"));
        settings.getGeneration().setTimeoutSeconds(1);
        var guarded=new RuntimeModelConfiguration().generationChatModel(settings);
        long started=System.nanoTime();
        assertThrows(RuntimeException.class,()->guarded.stream(new Prompt("test")).blockLast(Duration.ofSeconds(4)));
        assertTrue(Duration.ofNanos(System.nanoTime()-started).toMillis()<1900);
    }
}
