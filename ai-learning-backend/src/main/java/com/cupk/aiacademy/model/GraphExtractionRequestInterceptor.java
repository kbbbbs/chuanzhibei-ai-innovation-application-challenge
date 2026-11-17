package com.cupk.aiacademy.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Locale;

/** Applies graph-only request options that the installed Spring AI transport cannot serialize. */
public final class GraphExtractionRequestInterceptor implements ClientHttpRequestInterceptor {
    public static final String HEADER = "X-Local-Graph-Extraction";
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        boolean graphExtraction = "true".equals(request.getHeaders().getFirst(HEADER));
        request.getHeaders().remove(HEADER);
        if (!graphExtraction) return execution.execute(request, body);
        var input = json.readTree(body);
        if (!input.path("model").asText().toLowerCase(Locale.ROOT).startsWith("deepseek-v4"))
            return execution.execute(request, body);
        var payload = (ObjectNode) input;
        payload.set("thinking", json.createObjectNode().put("type", "disabled"));
        byte[] updated = json.writeValueAsBytes(payload);
        request.getHeaders().setContentLength(updated.length);
        return execution.execute(request, updated);
    }
}
