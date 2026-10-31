package com.cupk.aiacademy.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.cupk.aiacademy.model.RuntimeModelPolicy;
import com.cupk.aiacademy.model.RuntimeModelProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Optional foundation-model embeddings. No knowledge-service API is used. */
@Component
public class RagEmbeddingClient {
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final RuntimeModelProperties.Endpoint endpoint;
    private final HttpClient http;
    private final Duration timeout;

    @Autowired
    public RagEmbeddingClient(@Value("${runtime.models.embedding.enabled:${rag.embedding.enabled:false}}") boolean enabled,
                              @Value("${runtime.models.embedding.base-url:${rag.embedding.base-url:}}") String baseUrl,
                              @Value("${runtime.models.embedding.api-key:${rag.embedding.api-key:}}") String apiKey,
                              @Value("${runtime.models.embedding.model:${rag.embedding.model:}}") String model,
                              ObjectMapper mapper,
                              @Value("${runtime.models.embedding.timeout-seconds:30}") int timeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null ? "" : model.strip();
        this.mapper = mapper;
        this.timeout=Duration.ofSeconds(Math.max(1,timeoutSeconds));
        this.http=HttpClient.newBuilder().connectTimeout(timeout).build();
        this.endpoint = new RuntimeModelProperties.Endpoint();
        this.endpoint.setEnabled(enabled);
        this.endpoint.setBaseUrl(this.baseUrl);
        this.endpoint.setApiKey(this.apiKey);
        this.endpoint.setModel(this.model);
    }

    public RagEmbeddingClient(boolean enabled,String baseUrl,String apiKey,String model,ObjectMapper mapper) {
        this(enabled,baseUrl,apiKey,model,mapper,30);
    }

    public boolean isConfigured() { return "CONFIGURED_UNVERIFIED".equals(RuntimeModelPolicy.status(endpoint)); }
    public boolean isRequested() { return enabled; }
    public String modelName() { return model; }

    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((baseUrl.replaceAll("/+$", "") + "\n" + model).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot identify embedding model"); }
    }

    public List<double[]> embed(List<String> input) {
        RuntimeModelPolicy.requireReady(endpoint, null);
        List<double[]> result = new ArrayList<>();
        try {
            String path = baseUrl.replaceAll("/+$", "");
            if (!path.endsWith("/embeddings")) path += path.endsWith("/v1") ? "/embeddings" : "/v1/embeddings";
            URI uri = URI.create(path);
            String host = uri.getHost();
            if (host == null || !("https".equals(uri.getScheme()) ||
                    ("http".equals(uri.getScheme()) && List.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host))))
                throw new IllegalStateException("Embedding endpoint requires HTTPS or loopback HTTP");
            int dimensions = -1;
            // This provider's selected VL model resets indices in groups of eight for larger requests.
            // Send bounded independent batches; never infer identity from arrival order or renumber duplicates.
            int batchSize=model.endsWith("Qwen3-VL-Embedding-8B")?8:32;
            for (int start = 0; start < input.size(); start += batchSize) {
                List<String> batch = input.subList(start, Math.min(start + batchSize, input.size()));
                String body = mapper.writeValueAsString(Map.of("model", model, "input", batch, "encoding_format", "float"));
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                        .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                var pending=http.sendAsync(request,responseInfo->new BoundedEmbeddingBody());
                HttpResponse<byte[]> response;
                try {response=pending.get(timeout.toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS);}
                finally {if(!pending.isDone())pending.cancel(true);}
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new IllegalStateException("Embedding provider did not return a valid response");
                JsonNode data = mapper.readTree(response.body()).path("data");
                if (!data.isArray() || data.size() != batch.size()) throw new IllegalStateException("Incomplete embedding response");
                double[][] vectors = new double[batch.size()][];
                for (JsonNode item : data) {
                    JsonNode index = item.path("index");
                    if (!index.isIntegralNumber() || !index.canConvertToInt())
                        throw new IllegalStateException("Invalid vector index");
                    int n = index.asInt();
                    JsonNode values = item.path("embedding");
                    if (n < 0 || n >= vectors.length || vectors[n] != null || !values.isArray() ||
                            values.isEmpty() || values.size() > 4096) throw new IllegalStateException("Invalid vector data");
                    if (dimensions < 0) dimensions = values.size();
                    if (values.size() != dimensions) throw new IllegalStateException("Inconsistent vector dimensions");
                    double[] vector = new double[dimensions];
                    double norm = 0;
                    for (int j = 0; j < dimensions; j++) {
                        if (!values.get(j).isNumber()) throw new IllegalStateException("Invalid vector value");
                        vector[j] = values.get(j).asDouble();
                        if (!Double.isFinite(vector[j])) throw new IllegalStateException("Invalid vector value");
                        norm += vector[j] * vector[j];
                    }
                    if (!Double.isFinite(norm) || norm <= 0) throw new IllegalStateException("Invalid vector norm");
                    norm = Math.sqrt(norm);
                    for (int j = 0; j < dimensions; j++) vector[j] /= norm;
                    vectors[n] = vector;
                }
                for (double[] vector : vectors) {
                    if (vector == null) throw new IllegalStateException("Missing vector");
                    result.add(vector);
                }
            }
            return List.copyOf(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request interrupted");
        } catch (Exception e) {
            // Never expose provider responses, configured URL, credentials or exception causes to the API.
            throw new IllegalStateException("Embedding provider unavailable or returned incompatible vectors");
        }
    }

    /** Includes a stalled response body in the caller's deadline and cancels before unbounded retention. */
    private static final class BoundedEmbeddingBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        @Override public java.util.concurrent.CompletionStage<byte[]> getBody(){return result;}
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription value){subscription=value;value.request(1);}
        @Override public void onNext(List<java.nio.ByteBuffer> parts){
            for(var part:parts){
                if(part.remaining()>8_000_000-bytes.size()){
                    subscription.cancel();result.completeExceptionally(new IllegalStateException("Embedding response limit"));return;
                }
                byte[] next=new byte[part.remaining()];part.get(next);bytes.writeBytes(next);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error){result.completeExceptionally(error);}
        @Override public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
