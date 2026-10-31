package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.model.ModelConfigurationException;
import com.cupk.aiacademy.model.ModelInvocationException;
import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class OcrModelClientTest {
    private static final String FAKE_KEY = "ocr-unit-test-secret";
    private static final byte[] IMAGE = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };
    private final ObjectMapper mapper = new ObjectMapper();
    private final CopyOnWriteArrayList<Request> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ExecutorService executor;
    private RuntimeModelProperties settings;
    private volatile int responseStatus = 200;
    private volatile String responseBody;
    private volatile long responseDelayMillis;

    @BeforeEach
    void setup() throws Exception {
        responseBody = responseWithContent("recognized text");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        mapper.readTree(exchange.getRequestBody().readAllBytes())));
                if (responseDelayMillis > 0) Thread.sleep(responseDelayMillis);
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        settings = new RuntimeModelProperties();
        settings.getOcr().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        settings.getOcr().setApiKey(FAKE_KEY);
        settings.getOcr().setModel("deepseek-ai/DeepSeek-OCR");
        settings.getOcr().setTimeoutSeconds(5);
    }

    @AfterEach
    void cleanup() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void figureReadingUsesTheActualImageAndIndependentOcrEndpoint() {
        assertEquals("recognized text",client().recognizeFigure(IMAGE,"image/png").rawText());
        var body=requests.getFirst().body();
        assertEquals("<image>\nParse the figure.",body.path("messages").get(0).path("content").get(1).path("text").asText());
        assertEquals("deepseek-ai/DeepSeek-OCR",body.path("model").asText());
    }

    @Test
    void sendsConfiguredOcrModelAndImageThroughCompatibleHttpProtocol() {
        assertEquals("recognized text", client().recognize(IMAGE, "image/png"));

        assertEquals(1, requests.size());
        Request request = requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/v1/chat/completions", request.path());
        assertEquals("Bearer " + FAKE_KEY, request.authorization());
        assertTrue(request.contentType().startsWith("application/json"));
        JsonNode body = request.body();
        assertEquals("deepseek-ai/DeepSeek-OCR", body.path("model").asText());
        assertFalse(body.path("stream").asBoolean(true));
        assertEquals(0, body.path("temperature").asDouble());
        assertEquals(4096, body.path("max_tokens").asInt());
        assertEquals(1, body.path("messages").size());
        JsonNode message = body.path("messages").get(0);
        assertEquals("user", message.path("role").asText());
        JsonNode content = message.path("content");
        assertEquals(2, content.size());
        assertEquals("image_url", content.get(0).path("type").asText());
        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(IMAGE),
                content.get(0).path("image_url").path("url").asText());
        assertEquals("text", content.get(1).path("type").asText());
        assertEquals("<image>\n<|grounding|>OCR this image.", content.get(1).path("text").asText());
    }

    @Test void formulaDocumentModeRequestsStructuredMarkdownWithoutChangingTheProvider() {
        assertEquals("recognized text",client().recognizeDocument(IMAGE,"image/png").rawText());
        var body=requests.getFirst().body();
        assertEquals("<image>\n<|grounding|>Convert the document to markdown.",body.path("messages").get(0).path("content").get(1).path("text").asText());
        assertEquals("deepseek-ai/DeepSeek-OCR",body.path("model").asText());
    }

    @Test
    void acceptsJpegAndDoesNotDuplicateVersionPrefix() {
        settings.getOcr().setBaseUrl(settings.getOcr().getBaseUrl() + "/v1/");
        assertEquals("recognized text", client().recognize(IMAGE, "image/jpeg"));
        assertEquals("/v1/chat/completions", requests.getFirst().path());
        assertTrue(requests.getFirst().body().path("messages").get(0).path("content").get(0)
                .path("image_url").path("url").asText().startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void preservesRawOcrMarkupAndWhitespaceForLaterNormalization() throws Exception {
        String raw = "  <|ref|>title<|/ref|><|det|>[[1, 2, 3, 4]]<|/det|>\n课程：人工智能\n ";
        responseBody = responseWithContent(raw);
        assertEquals(raw, client().recognize(IMAGE, "image/png"));
    }

    @Test
    void missingConfigurationDoesNotSendARequest() {
        settings.getOcr().setApiKey("");
        assertEquals("MODEL_NOT_CONFIGURED", assertThrows(ModelConfigurationException.class,
                () -> client().recognize(IMAGE, "image/png")).code());
        assertTrue(requests.isEmpty());
    }

    @Test
    void modelPolicyCannotBeBypassed() {
        settings.getOcr().setModel("gpt-4o");
        assertEquals("MODEL_BLOCKED", assertThrows(ModelConfigurationException.class,
                () -> client().recognize(IMAGE, "image/png")).code());
        assertTrue(requests.isEmpty());
    }

    @Test
    void invalidImagesDoNotSendRequests() {
        for (byte[] invalid : new byte[][] { null, new byte[0], new byte[10 * 1024 * 1024 + 1] }) {
            assertEquals("OCR_IMAGE_INVALID", assertThrows(ModelConfigurationException.class,
                    () -> client().recognize(invalid, "image/png")).code());
        }
        for (String invalidMime : new String[] { null, "", "application/pdf", "image/svg+xml" }) {
            assertEquals("OCR_IMAGE_INVALID", assertThrows(ModelConfigurationException.class,
                    () -> client().recognize(IMAGE, invalidMime)).code());
        }
        assertTrue(requests.isEmpty());
    }

    @Test
    void invalidTimeoutDoesNotSendARequest() {
        settings.getOcr().setTimeoutSeconds(0);
        assertEquals("MODEL_TIMEOUT_INVALID", assertThrows(ModelConfigurationException.class,
                () -> client().recognize(IMAGE, "image/png")).code());
        assertTrue(requests.isEmpty());
    }

    @Test
    void malformedAndEmptyResponsesHaveSafeStableErrors() {
        for (String invalid : new String[] { "not-json", "{}", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}",
                "{\"choices\":[{\"message\":{\"content\":null}}]}",
                "{\"choices\":[{\"message\":{\"content\":[]}}]}" }) {
            responseBody = invalid;
            ModelInvocationException error = assertThrows(ModelInvocationException.class,
                    () -> client().recognize(IMAGE, "image/png"));
            assertEquals("OCR_INVALID_RESPONSE", error.code());
            assertSafe(error);
        }
    }

    @Test
    void providerAuthenticationFailureDoesNotExposeSecretOrSupplierBody() {
        responseStatus = 401;
        responseBody = "{\"error\":\"" + FAKE_KEY + " private-provider-diagnostic\"}";
        ModelInvocationException error = assertThrows(ModelInvocationException.class,
                () -> client().recognize(IMAGE, "image/png"));
        assertEquals("MODEL_HTTP_401", error.code());
        assertSafe(error);
        assertFalse(error.getMessage().contains("private-provider-diagnostic"));
        assertEquals(1, requests.size());
    }

    @Test
    void oversizedResponseIsRejectedWithoutKeepingSupplierBody() {
        responseBody = "x".repeat(4 * 1024 * 1024 + 1);
        ModelInvocationException error = assertThrows(ModelInvocationException.class,
                () -> client().recognize(IMAGE, "image/png"));
        assertEquals("OCR_RESPONSE_TOO_LARGE", error.code());
        assertSafe(error);
    }

    @Test
    void configuredTimeoutAppliesToTheActualRequest() {
        responseDelayMillis = 5000;
        settings.getOcr().setTimeoutSeconds(1);
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            ModelInvocationException error = assertThrows(ModelInvocationException.class,
                    () -> client().recognize(IMAGE, "image/png"));
            assertEquals("MODEL_TIMEOUT", error.code());
            assertSafe(error);
        });
        assertEquals(1, requests.size());
    }

    @ParameterizedTest
    @CsvSource({"length,OCR_OUTPUT_TRUNCATED","content_filter,OCR_CONTENT_FILTERED","tool_calls,OCR_OUTPUT_INCOMPLETE"})
    void explicitIncompleteFinishReasonsPreserveReviewTextButNeverCountAsRecognized(String reason,String code) throws Exception {
        responseBody="{\"choices\":[{\"finish_reason\":\""+reason+"\",\"message\":{\"content\":\"partial recognized text\"}}]}";
        var detailed=client().recognizeDetailed(IMAGE,"image/png");
        assertEquals("partial recognized text",detailed.rawText());assertEquals(code,detailed.errorCode());
        var failure=assertThrows(ModelInvocationException.class,()->client().recognize(IMAGE,"image/png"));
        assertEquals(code,failure.code());assertSafe(failure);assertFalse(failure.getMessage().contains("partial recognized text"));
    }

    private OcrModelClient client() {
        return new OcrModelClient(settings, mapper);
    }

    private String responseWithContent(String content) throws Exception {
        return "{\"choices\":[{\"message\":{\"content\":" + mapper.writeValueAsString(content) + "}}]}";
    }

    private static void assertSafe(ModelInvocationException error) {
        assertFalse(error.getMessage().contains(FAKE_KEY));
        assertNull(error.getCause());
    }

    private record Request(String method, String path, String authorization, String contentType, JsonNode body) {}
}
