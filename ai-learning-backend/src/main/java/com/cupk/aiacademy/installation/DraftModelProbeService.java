package com.cupk.aiacademy.installation;

import com.cupk.aiacademy.model.RuntimeModelCapabilityService.Check;
import com.cupk.aiacademy.model.RuntimeModelCapabilityService.ProbeReport;
import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Explicit, bounded draft probes never touch the active clients or save a revision. */
public final class DraftModelProbeService {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final InstallationConfigStore store;
    private final ObjectMapper json = new ObjectMapper();

    public DraftModelProbeService(InstallationConfigStore store) {
        this.store = store;
    }

    public ProbeReport probe(String role, Map<String, Map<String, Object>> roles) {
        RuntimeModelProperties.Endpoint endpoint = store.resolveDraft(role, roles);
        if (!endpoint.isEnabled())
            return new ProbeReport(
                    role,
                    "SKIPPED",
                    List.of(new Check("CONFIGURATION", "SKIPPED", "MODEL_DISABLED", 0, 0)));
        store.requireAllowedEndpoint(endpoint.getBaseUrl());
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        try (HttpClient client =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(
                                Duration.ofSeconds(Math.min(30, endpoint.getTimeoutSeconds())))
                        .build()) {
            var probe = new Probe(client, endpoint, deadline);
            List<Check> checks = new ArrayList<>();
            if (role.equals("embedding")) {
                checks.add(
                        check(
                                "EMBEDDING",
                                0,
                                () -> {
                                    JsonNode result =
                                            json.readTree(
                                                    probe.call(
                                                            "embeddings",
                                                            Map.of(
                                                                    "model",
                                                                    endpoint.getModel(),
                                                                    "input",
                                                                    List.of(
                                                                            "Gradient descent"
                                                                                + " updates"
                                                                                + " parameters.",
                                                                            "Photosynthesis"
                                                                                + " converts light"
                                                                                + " into chemical"
                                                                                + " energy."),
                                                                    "encoding_format",
                                                                    "float")));
                                    JsonNode data = result.path("data");
                                    if (!data.isArray() || data.size() != 2) return false;
                                    JsonNode first = data.get(0).path("embedding"),
                                            second = data.get(1).path("embedding");
                                    if (!first.isArray()
                                            || first.isEmpty()
                                            || first.size() != second.size()
                                            || first.equals(second)) return false;
                                    for (JsonNode vector : List.of(first, second))
                                        for (JsonNode number : vector)
                                            if (!number.isNumber()
                                                    || !Double.isFinite(number.doubleValue()))
                                                return false;
                                    return true;
                                }));
            } else if (role.equals("ocr")) {
                checks.add(
                        check(
                                "OCR_IMAGE",
                                0,
                                () -> {
                                    String encoded =
                                            Base64.getEncoder().encodeToString(syntheticImage());
                                    var content =
                                            List.of(
                                                    Map.of(
                                                            "type",
                                                            "image_url",
                                                            "image_url",
                                                            Map.of(
                                                                    "url",
                                                                    "data:image/png;base64,"
                                                                            + encoded)),
                                                    Map.of(
                                                            "type",
                                                            "text",
                                                            "text",
                                                            "<image>\n"
                                                                + "<|grounding|>OCR this"
                                                                + " image."));
                                    String response =
                                            probe.call(
                                                    "chat/completions",
                                                    Map.of(
                                                            "model",
                                                            endpoint.getModel(),
                                                            "messages",
                                                            List.of(
                                                                    Map.of(
                                                                            "role", "user",
                                                                            "content", content)),
                                                            "stream",
                                                            false,
                                                            "temperature",
                                                            0,
                                                            "max_tokens",
                                                            512));
                                    return content(response)
                                            .replaceAll("\\s+", " ")
                                            .contains("OCR TEST 2026");
                                }));
            } else {
                checks.add(
                        check(
                                "TEXT",
                                0,
                                () ->
                                        "ok"
                                                .equals(
                                                        content(
                                                                        probe.chat(
                                                                                "Reply with"
                                                                                    + " exactly:"
                                                                                    + " ok",
                                                                                false,
                                                                                false))
                                                                .strip())));
                if (checks.getFirst().status().equals("PASS")) {
                    checks.add(
                            check(
                                    "JSON_OBJECT",
                                    0,
                                    () -> {
                                        var value =
                                                json.readTree(
                                                        content(
                                                                probe.chat(
                                                                        "Return JSON only:"
                                                                            + " {\"ok\":true}",
                                                                        true,
                                                                        false)));
                                        return value.isObject()
                                                && value.path("ok").isBoolean()
                                                && value.path("ok").asBoolean();
                                    }));
                    checks.add(
                            check(
                                    "STREAM",
                                    0,
                                    () -> {
                                        String response =
                                                probe.chat("Reply with exactly: ok", false, true);
                                        StringBuilder text = new StringBuilder();
                                        boolean done = false;
                                        for (String line : response.split("\\R")) {
                                            if (!line.startsWith("data:")) continue;
                                            String data = line.substring(5).strip();
                                            if (data.equals("[DONE]")) {
                                                done = true;
                                                continue;
                                            }
                                            JsonNode parsed = json.readTree(data);
                                            text.append(
                                                    parsed.path("choices")
                                                            .path(0)
                                                            .path("delta")
                                                            .path("content")
                                                            .asText(""));
                                        }
                                        return done && text.toString().strip().equals("ok");
                                    }));
                    String sentinel =
                            "K00_SENTINEL_" + UUID.randomUUID().toString().replace("-", "");
                    String sample =
                            sentinel
                                    + "\n"
                                    + "A synthetic course passage for a bounded context test. "
                                            .repeat(180)
                                    + "\n"
                                    + sentinel;
                    checks.add(
                            check(
                                    "CONTEXT_SAMPLE",
                                    sample.length(),
                                    () ->
                                            sentinel.equals(
                                                    content(
                                                                    probe.chat(
                                                                            "Read the sample and"
                                                                                + " return only its"
                                                                                + " K00_SENTINEL"
                                                                                + " identifier.\n"
                                                                                    + sample,
                                                                            false,
                                                                            false))
                                                            .strip())));
                } else {
                    for (String name : List.of("JSON_OBJECT", "STREAM", "CONTEXT_SAMPLE"))
                        checks.add(new Check(name, "SKIPPED", "TEXT_PROBE_FAILED", 0, 0));
                }
            }
            return new ProbeReport(
                    role,
                    checks.stream().allMatch(c -> c.status().equals("PASS")) ? "PASS" : "FAIL",
                    List.copyOf(checks));
        }
    }

    private String content(String body) throws Exception {
        JsonNode root = json.readTree(body);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    @FunctionalInterface
    private interface Test {
        boolean run() throws Exception;
    }

    private Check check(String name, int characters, Test test) {
        long start = System.nanoTime();
        String status = "PASS", detail = "OBSERVED_AT_TESTED_SIZE_ONLY";
        try {
            if (!test.run()) {
                status = "FAIL";
                detail = "UNEXPECTED_RESPONSE";
            }
        } catch (Exception error) {
            status = "FAIL";
            detail =
                    error instanceof TimeoutException || error instanceof HttpTimeoutException
                            ? "MODEL_TIMEOUT"
                            : "MODEL_CALL_FAILED";
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return new Check(
                name,
                status,
                detail,
                Duration.ofNanos(System.nanoTime() - start).toMillis(),
                characters);
    }

    private byte[] syntheticImage() throws Exception {
        var image =
                new java.awt.image.BufferedImage(
                        1100, 360, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, 1100, 360);
            graphics.setColor(java.awt.Color.BLACK);
            graphics.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 64));
            graphics.drawString("OCR TEST 2026", 60, 200);
        } finally {
            graphics.dispose();
        }
        try (var output = new ByteArrayOutputStream()) {
            if (!javax.imageio.ImageIO.write(image, "png", output))
                throw new IllegalStateException();
            return output.toByteArray();
        }
    }

    private final class Probe {
        private final HttpClient client;
        private final RuntimeModelProperties.Endpoint endpoint;
        private final long deadline;

        private Probe(HttpClient client, RuntimeModelProperties.Endpoint endpoint, long deadline) {
            this.client = client;
            this.endpoint = endpoint;
            this.deadline = deadline;
        }

        private String chat(String prompt, boolean object, boolean stream) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", endpoint.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", stream);
            body.put("temperature", 0);
            body.put("max_tokens", 100);
            if (object) body.put("response_format", Map.of("type", "json_object"));
            return call("chat/completions", body);
        }

        private String call(String route, Map<String, ?> body) throws Exception {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || Thread.currentThread().isInterrupted())
                throw new TimeoutException();
            Duration timeout =
                    Duration.ofNanos(
                            Math.min(
                                    remaining,
                                    Duration.ofSeconds(Math.min(30, endpoint.getTimeoutSeconds()))
                                            .toNanos()));
            String base = endpoint.getBaseUrl().replaceAll("/+$", "");
            String path = base.endsWith("/v1") ? "/" + route : "/v1/" + route;
            var request =
                    HttpRequest.newBuilder(URI.create(base + path))
                            .timeout(timeout)
                            .header("Authorization", "Bearer " + endpoint.getApiKey())
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofByteArray(
                                            json.writeValueAsBytes(body)))
                            .build();
            CompletableFuture<HttpResponse<byte[]>> pending =
                    client.sendAsync(request, ignored -> new BoundedBody());
            try {
                var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new IllegalStateException();
                return new String(response.body(), StandardCharsets.UTF_8);
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException());
                    return;
                }
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                bytes.writeBytes(data);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(new IllegalStateException());
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
