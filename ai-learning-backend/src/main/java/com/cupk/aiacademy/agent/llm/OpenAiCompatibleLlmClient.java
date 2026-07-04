package com.cupk.aiacademy.agent.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final LlmProperties properties;
    private final OkHttpClient client;

    public OpenAiCompatibleLlmClient(LlmProperties properties) {
        this.properties = properties;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String chat(LlmChatRequest request) throws Exception {
        JSONObject payload = buildPayload(request, false);
        Request httpRequest = buildHttpRequest(payload);

        try (Response response = client.newCall(httpRequest).execute()) {
            if (response.body() == null) {
                throw new IOException("LLM response body is empty");
            }
            String body = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("LLM " + response.code() + ": " + body);
            }
            JSONObject json = JSONObject.parseObject(body);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "";
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            return message == null ? "" : message.getString("content");
        }
    }

    @Override
    public void streamChat(LlmChatRequest request, LlmStreamHandler handler) throws Exception {
        JSONObject payload = buildPayload(request, true);
        Request httpRequest = buildHttpRequest(payload);

        try (Response response = client.newCall(httpRequest).execute()) {
            if (response.body() == null) {
                throw new IOException("LLM response body is empty");
            }
            if (!response.isSuccessful()) {
                throw new IOException("LLM " + response.code() + ": " + response.body().string());
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        continue;
                    }
                    String delta = extractDelta(data);
                    if (delta != null && !delta.isEmpty()) {
                        handler.onDelta(delta);
                    }
                }
            }
        }
    }

    private JSONObject buildPayload(LlmChatRequest request, boolean stream) {
        ensureConfigured();

        JSONObject payload = new JSONObject();
        payload.put("model", firstNotBlank(request.getModel(), properties.getModel()));
        payload.put("temperature", request.getTemperature() == null
                ? properties.getTemperature()
                : request.getTemperature());
        payload.put("stream", stream);

        JSONArray messages = new JSONArray();
        for (LlmMessage message : request.getMessages()) {
            JSONObject item = new JSONObject();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            messages.add(item);
        }
        payload.put("messages", messages);
        return payload;
    }

    private Request buildHttpRequest(JSONObject payload) {
        return new Request.Builder()
                .url(normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(payload.toJSONString(), JSON_TYPE))
                .build();
    }

    private String extractDelta(String data) {
        JSONObject json = JSONObject.parseObject(data);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
        return delta == null ? "" : delta.getString("content");
    }

    private void ensureConfigured() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("请先配置 LLM_API_KEY");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = firstNotBlank(baseUrl, "https://api.openai.com/v1");
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String firstNotBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
