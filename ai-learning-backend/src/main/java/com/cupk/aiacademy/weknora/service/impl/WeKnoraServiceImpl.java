package com.cupk.aiacademy.weknora.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.config.WeKnoraConfig;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class WeKnoraServiceImpl implements WeKnoraService {

    private static final Logger log = LoggerFactory.getLogger(WeKnoraServiceImpl.class);

    @Autowired
    private WeKnoraConfig config;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    @Override
    public Result search(WeKnoraSearchRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", request.getQuery());
            body.put("knowledge_base_ids", request.getKnowledgeBaseIds());

            String resp = post("/api/v1/knowledge-search", body);
            return Result.success(JSON.parse(resp));
        } catch (Exception e) {
            log.error("WeKnora search error", e);
            return Result.error("知识检索失败: " + e.getMessage());
        }
    }

    @Override
    public Result createSession(WeKnoraSessionRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("knowledge_base_id", request.getKnowledgeBaseId());
            body.put("title", request.getTitle());
            if (request.getAgentId() != null) body.put("agent_id", request.getAgentId());
            if (request.getStrategy() != null) body.put("strategy", request.getStrategy());

            return Result.success(JSON.parse(post("/api/v1/sessions", body)));
        } catch (Exception e) {
            log.error("WeKnora createSession error", e);
            return Result.error("创建会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result listSessions() {
        try {
            return Result.success(JSON.parse(get("/api/v1/sessions")));
        } catch (Exception e) {
            log.error("WeKnora listSessions error", e);
            return Result.error("获取会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteSession(String sessionId) {
        try {
            return Result.success(JSON.parse(delete("/api/v1/sessions/" + sessionId)));
        } catch (Exception e) {
            log.error("WeKnora deleteSession error", e);
            return Result.error("删除会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result stopSession(String sessionId) {
        try {
            return Result.success(JSON.parse(post("/api/v1/sessions/" + sessionId + "/stop", Map.of())));
        } catch (Exception e) {
            log.error("WeKnora stopSession error", e);
            return Result.error("停止会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result listKnowledgeBases() {
        try {
            return Result.success(JSON.parse(get("/api/v1/knowledge-bases")));
        } catch (Exception e) {
            log.error("WeKnora listKnowledgeBases error", e);
            return Result.error("获取知识库列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result uploadFile(String knowledgeBaseId, byte[] fileBytes, String fileName) {
        try {
            RequestBody fileBody = RequestBody.create(fileBytes,
                    MediaType.parse("application/octet-stream"));

            MultipartBody multipartBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, fileBody)
                    .build();

            Request httpRequest = new Request.Builder()
                    .url(config.getBaseUrl() + "/api/v1/knowledge-bases/" + knowledgeBaseId + "/knowledge/file")
                    .header("X-API-Key", config.getApiKey())
                    .post(multipartBody)
                    .build();

            try (Response response = client.newCall(httpRequest).execute()) {
                assert response.body() != null;
                String body = response.body().string();
                if (!response.isSuccessful()) return Result.error("上传失败: " + body);
                return Result.success(JSON.parse(body));
            }
        } catch (Exception e) {
            log.error("WeKnora uploadFile error", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter knowledgeChat(String sessionId, String query) {
        return streamChat("/api/v1/knowledge-chat/" + sessionId, query);
    }

    @Override
    public SseEmitter agentChat(String sessionId, String query, String agentId) {
        return streamChat("/api/v1/agent-chat/" + sessionId, query);
    }

    private SseEmitter streamChat(String path, String query) {
        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                Map<String, Object> bodyMap = new LinkedHashMap<>();
                bodyMap.put("query", query);
                String jsonBody = JSON.toJSONString(bodyMap);

                Request httpRequest = new Request.Builder()
                        .url(config.getBaseUrl() + path)
                        .header("X-API-Key", config.getApiKey())
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON_TYPE))
                        .build();

                try (Response response = client.newCall(httpRequest).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        Map<String, Object> err = Map.of("error", "WeKnora API error: " + response.code());
                        emitter.send(SseEmitter.event().name("error").data(err));
                        emitter.complete();
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) {
                                emitter.send(SseEmitter.event().name("done").data(""));
                                break;
                            }
                            emitter.send(SseEmitter.event().name("message").data(data));
                        }
                    }
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("WeKnora streamChat IO error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("error", e.getMessage())));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("WeKnora streamChat error", e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private String get(String path) throws IOException {
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + path)
                .header("X-API-Key", config.getApiKey())
                .get().build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            String body = response.body().string();
            if (!response.isSuccessful()) throw new IOException("WeKnora " + response.code() + ": " + body);
            return body;
        }
    }

    private String post(String path, Map<String, Object> bodyMap) throws IOException {
        String jsonBody = bodyMap.isEmpty() ? "{}" : JSON.toJSONString(bodyMap);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + path)
                .header("X-API-Key", config.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            String body = response.body().string();
            if (!response.isSuccessful()) throw new IOException("WeKnora " + response.code() + ": " + body);
            return body;
        }
    }

    private String delete(String path) throws IOException {
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + path)
                .header("X-API-Key", config.getApiKey())
                .delete().build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            String body = response.body().string();
            if (!response.isSuccessful()) throw new IOException("WeKnora " + response.code() + ": " + body);
            return body;
        }
    }
}
