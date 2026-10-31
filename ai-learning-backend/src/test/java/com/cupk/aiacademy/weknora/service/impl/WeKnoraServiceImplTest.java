package com.cupk.aiacademy.weknora.service.impl;

import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.config.WeKnoraConfig;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.service.OcrService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WeKnoraServiceImplTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void fallsBackToParsedDocumentSummariesWhenSemanticSearchIsEmpty() throws Exception {
        AtomicInteger semanticCalls = new AtomicInteger();
        AtomicInteger documentCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/knowledge-search", exchange -> {
            semanticCalls.incrementAndGet();
            respond(exchange, 200, "{\"success\":true,\"data\":[]}");
        });
        server.createContext("/api/v1/knowledge-bases/kb-1/knowledge", exchange -> {
            documentCalls.incrementAndGet();
            respond(exchange, 200, """
                    {"success":true,"data":[
                      {"id":"doc-1","title":"人工智能绪论.pdf","description":"人工智能课程介绍机器学习、神经网络和自然语言处理。","parse_status":"completed","enable_status":"enabled"},
                      {"id":"doc-2","title":"数据库.pdf","description":"数据库事务和索引基础。","parse_status":"completed","enable_status":"enabled"}
                    ]}
                    """);
        });
        server.start();

        WeKnoraConfig config = new WeKnoraConfig();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setApiKey("test-key");
        config.setSearchTimeoutSeconds(1);
        WeKnoraServiceImpl service = new WeKnoraServiceImpl(config, mock(OcrService.class));

        Result result = service.search(new WeKnoraSearchRequest(List.of("kb-1"), "人工智能基础", 5, null));

        assertEquals("200", result.getCode());
        Collection<?> hits = (Collection<?>) result.getData();
        assertEquals(2, hits.size());
        Map<?, ?> first = (Map<?, ?>) hits.iterator().next();
        assertEquals("document_summary_fallback", first.get("retrieval_mode"));
        assertTrue(String.valueOf(first.get("content")).contains("人工智能课程"));
        assertEquals(1, semanticCalls.get());
        assertEquals(1, documentCalls.get());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WEKNORA_INTEGRATION", matches = "true")
    void retrievesContextFromRunningWeKnora() {
        WeKnoraConfig config = new WeKnoraConfig();
        config.setBaseUrl(System.getenv().getOrDefault("WEKNORA_BASE_URL", "http://127.0.0.1:8090"));
        config.setApiKey(System.getenv("WEKNORA_API_KEY"));
        config.setSearchTimeoutSeconds(2);
        WeKnoraServiceImpl service = new WeKnoraServiceImpl(config, mock(OcrService.class));

        Result result = service.search(new WeKnoraSearchRequest(
                List.of(System.getenv("WEKNORA_KNOWLEDGE_BASE_ID")), "人工智能基础", 5, null));

        assertEquals("200", result.getCode());
        Collection<?> hits = (Collection<?>) result.getData();
        assertTrue(!hits.isEmpty());
        assertTrue(String.valueOf(hits.iterator().next()).contains("人工智能"));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
