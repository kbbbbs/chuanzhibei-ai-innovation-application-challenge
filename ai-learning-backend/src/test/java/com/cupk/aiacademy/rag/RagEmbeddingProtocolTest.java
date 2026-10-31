package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic local HTTP contracts only; these tests do not establish provider quality. */
class RagEmbeddingProtocolTest {
    private static final String MODEL = "Qwen/Qwen3-VL-Embedding-8B";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsSelectedModelsNative4096DimensionsAndNormalizesTheVector() throws Exception {
        double[] nativeVector = new double[4096];
        java.util.Arrays.fill(nativeVector, 0.125);
        String response = mapper.writeValueAsString(Map.of("data", List.of(
                Map.of("index", 0, "embedding", nativeVector))));
        try (var provider = new LocalProvider(response)) {
            var vectors = provider.client().embed(List.of("course document"));
            assertEquals(1, vectors.size());
            assertEquals(4096, vectors.getFirst().length);
            assertEquals(1.0 / 64, vectors.getFirst()[4095], 1e-12);
            assertEquals(1.0, java.util.Arrays.stream(vectors.getFirst()).map(v -> v * v).sum(), 1e-12);
            var request = mapper.readTree(provider.requestBody.get());
            assertEquals(MODEL, request.path("model").asText());
            assertEquals("float", request.path("encoding_format").asText());
            assertEquals("course document", request.path("input").get(0).asText());
        }
    }

    @Test
    void restoresInputOrderFromReturnedIndices() throws Exception {
        try (var provider = new LocalProvider("""
                {"data":[{"index":1,"embedding":[0,3]},{"index":0,"embedding":[4,0]}]}
                """)) {
            var vectors = provider.client().embed(List.of("first document", "second document"));
            assertArrayEquals(new double[]{1, 0}, vectors.get(0), 1e-12);
            assertArrayEquals(new double[]{0, 1}, vectors.get(1), 1e-12);
        }
    }

    @Test
    void rejectsVectorsAboveSupported4096DimensionsWithoutDisclosingProviderDetails() throws Exception {
        double[] oversized = new double[4097];
        java.util.Arrays.fill(oversized, 0.125);
        String response = mapper.writeValueAsString(Map.of("data", List.of(
                Map.of("index", 0, "embedding", oversized))));
        try (var provider = new LocalProvider(response)) {
            assertSanitizedFailure(() -> provider.client().embed(List.of("document")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"embedding\":[1,0]},{\"index\":1,\"embedding\":[0,1]}]",
            "[{\"index\":0.5,\"embedding\":[1,0]},{\"index\":1,\"embedding\":[0,1]}]",
            "[{\"index\":4294967296,\"embedding\":[1,0]},{\"index\":1,\"embedding\":[0,1]}]",
            "[{\"index\":0,\"embedding\":[1,0]},{\"index\":0,\"embedding\":[0,1]}]",
            "[{\"index\":2,\"embedding\":[1,0]},{\"index\":0,\"embedding\":[0,1]}]",
            "[{\"index\":-1,\"embedding\":[1,0]},{\"index\":1,\"embedding\":[0,1]}]",
            "[{\"index\":0,\"embedding\":[1,0]}]"
    })
    void rejectsIncompleteOrAmbiguousResponseIndices(String items) throws Exception {
        try (var provider = new LocalProvider("{\"data\":" + items + "}")) {
            assertSanitizedFailure(() -> provider.client().embed(List.of("first", "second")));
        }
    }

    @Test
    void rejectsDifferentVectorDimensionsWithinOneResponse() throws Exception {
        try (var provider = new LocalProvider("""
                {"data":[{"index":0,"embedding":[1,0]},{"index":1,"embedding":[0,1,0]}]}
                """)) {
            assertSanitizedFailure(() -> provider.client().embed(List.of("first", "second")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[0,0]", "[\"private-provider-content\",1]"})
    void rejectsEmptyZeroOrNonnumericVectors(String values) throws Exception {
        try (var provider = new LocalProvider("{\"data\":[{\"index\":0,\"embedding\":" + values + "}]}")) {
            assertSanitizedFailure(() -> provider.client().embed(List.of("document")));
        }
    }

    private void assertSanitizedFailure(org.junit.jupiter.api.function.Executable call) {
        var failure = assertThrows(IllegalStateException.class, call);
        assertEquals("Embedding provider unavailable or returned incompatible vectors", failure.getMessage());
        assertNull(failure.getCause(), "Provider bodies, endpoint and credentials must not escape through causes");
    }

    private class LocalProvider implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        private LocalProvider(String response) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/embeddings", exchange -> {
                try {
                    requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        private RagEmbeddingClient client() {
            return new RagEmbeddingClient(true, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "synthetic-test-key", MODEL, mapper, 5);
        }

        @Override public void close() { server.stop(0); }
    }
}
