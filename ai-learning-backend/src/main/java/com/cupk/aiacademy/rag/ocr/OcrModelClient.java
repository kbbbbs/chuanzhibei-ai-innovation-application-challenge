package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.model.ModelConfigurationException;
import com.cupk.aiacademy.model.ModelInvocationException;
import com.cupk.aiacademy.model.RuntimeModelPolicy;
import com.cupk.aiacademy.model.RuntimeModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Image-only OCR transport. PDF rendering and OCR markup normalization belong to later pipeline steps. */
@Component
public class OcrModelClient {
    /** A successful HTTP response can still be incomplete. Raw recognized text is for private review only. */
    public record Recognition(String rawText,String errorCode) { }
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private final RuntimeModelProperties properties;
    private final ObjectMapper mapper;

    public OcrModelClient(RuntimeModelProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public String recognize(byte[] image, String mimeType) {
        Recognition result=recognizeDetailed(image,mimeType);
        if(result.errorCode()!=null)throw new ModelInvocationException(result.errorCode());
        return result.rawText();
    }

    public Recognition recognizeDetailed(byte[] image,String mimeType) {
        return recognizeWithPrompt(image,mimeType,"<image>\n<|grounding|>OCR this image.");
    }

    public Recognition recognizeDocument(byte[] image,String mimeType) {
        return recognizeWithPrompt(image,mimeType,"<image>\n<|grounding|>Convert the document to markdown.");
    }

    /** Figure reading is still performed by the independently configured, non-GPT OCR endpoint. */
    public Recognition recognizeFigure(byte[] image,String mimeType) {
        return recognizeWithPrompt(image,mimeType,"<image>\nParse the figure.");
    }

    private Recognition recognizeWithPrompt(byte[] image,String mimeType,String prompt) {
        RuntimeModelProperties.Endpoint endpoint = properties.getOcr();
        if (endpoint == null) throw new ModelConfigurationException("MODEL_NOT_CONFIGURED");
        RuntimeModelPolicy.requireReady(endpoint, null);
        if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES
                || !("image/png".equals(mimeType) || "image/jpeg".equals(mimeType)))
            throw new ModelConfigurationException("OCR_IMAGE_INVALID");
        if (endpoint.getTimeoutSeconds() <= 0)
            throw new ModelConfigurationException("MODEL_TIMEOUT_INVALID");

        HttpRequest request;
        Duration timeout = Duration.ofSeconds(endpoint.getTimeoutSeconds());
        try {
            String base = endpoint.getBaseUrl().replaceAll("/+$", "");
            String path = base.endsWith("/v1") ? "/chat/completions" : "/v1/chat/completions";
            Map<String, Object> body = Map.of(
                    "model", endpoint.getModel(),
                    "messages", List.of(Map.of("role", "user", "content", List.of(
                            Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mimeType
                                    + ";base64," + Base64.getEncoder().encodeToString(image))),
                            Map.of("type", "text", "text", prompt)))),
                    "stream", false, "temperature", 0, "max_tokens", 4096);
            request = HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout)
                    .header("Authorization", "Bearer " + endpoint.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
        } catch (Exception ignored) {
            throw new ModelConfigurationException("MODEL_REQUEST_INVALID");
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            pending = client.sendAsync(request, response -> new BoundedBodySubscriber());
            // The future deadline includes response-body delivery, including a stalled body after headers.
            HttpResponse<byte[]> response = pending.get(endpoint.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ModelInvocationException("MODEL_HTTP_" + response.statusCode());
            JsonNode content,choice;
            try {
                choice=mapper.readTree(response.body()).path("choices").path(0);
                content=choice.path("message").path("content");
            } catch (Exception ignored) {
                throw new ModelInvocationException("OCR_INVALID_RESPONSE");
            }
            if (!content.isTextual() || content.textValue().isBlank())
                throw new ModelInvocationException("OCR_INVALID_RESPONSE");
            JsonNode finish=choice.get("finish_reason");
            String reason=finish==null || finish.isNull()?null:finish.asText();
            String error=reason==null || "stop".equals(reason)?null:switch(reason) {
                case "length" -> "OCR_OUTPUT_TRUNCATED";
                case "content_filter" -> "OCR_CONTENT_FILTERED";
                default -> "OCR_OUTPUT_INCOMPLETE";
            };
            return new Recognition(content.textValue(),error);
        } catch (ModelInvocationException safe) {
            throw safe;
        } catch (TimeoutException ignored) {
            throw new ModelInvocationException("MODEL_TIMEOUT");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new ModelInvocationException("MODEL_CALL_INTERRUPTED");
        } catch (ExecutionException failure) {
            throw safeInvocationFailure(failure);
        } catch (RuntimeException ignored) {
            throw new ModelInvocationException("MODEL_CALL_FAILED");
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            client.shutdownNow();
        }
    }

    private static ModelInvocationException safeInvocationFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) return new ModelInvocationException("MODEL_TIMEOUT");
            if (cause instanceof ModelInvocationException safe) return new ModelInvocationException(safe.code());
        }
        return new ModelInvocationException("MODEL_CALL_FAILED");
    }

    /** Cancels the response subscription before an unbounded provider body can be retained. */
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() { return result; }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer next : buffers) {
                if (next.remaining() > MAX_RESPONSE_BYTES - buffer.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new ModelInvocationException("OCR_RESPONSE_TOO_LARGE"));
                    return;
                }
                byte[] bytes = new byte[next.remaining()];
                next.get(bytes);
                buffer.writeBytes(bytes);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable failure) { result.completeExceptionally(failure); }

        @Override
        public void onComplete() { result.complete(buffer.toByteArray()); }
    }
}
