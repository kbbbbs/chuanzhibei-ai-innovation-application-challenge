package com.cupk.aiacademy.weknora.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.config.WeKnoraConfig;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest;
import com.cupk.aiacademy.weknora.service.OcrService;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class WeKnoraServiceImpl implements WeKnoraService {

    private static final Logger log = LoggerFactory.getLogger(WeKnoraServiceImpl.class);

    @Autowired
    private WeKnoraConfig config;

    @Autowired
    private OcrService ocrService;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private Object parseWeKnoraBody(String json) {
        Map<String, Object> map = JSON.parseObject(json, Map.class);
        if (map == null) return null;
        Object data = map.get("data");
        while (data instanceof Map && ((Map<?, ?>) data).containsKey("data")) {
            data = ((Map<?, ?>) data).get("data");
        }
        return data;
    }

    @Override
    public Result search(WeKnoraSearchRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", request.getQuery());
            body.put("knowledge_base_ids", request.getKnowledgeBaseIds());

            String resp = post("/api/v1/knowledge-search", body);
            return Result.success(parseWeKnoraBody(resp));
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

            return Result.success(parseWeKnoraBody(post("/api/v1/sessions", body)));
        } catch (Exception e) {
            log.error("WeKnora createSession error", e);
            return Result.error("创建会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result listSessions() {
        try {
            return Result.success(parseWeKnoraBody(get("/api/v1/sessions")));
        } catch (Exception e) {
            log.error("WeKnora listSessions error", e);
            return Result.error("获取会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteSession(String sessionId) {
        try {
            return Result.success(parseWeKnoraBody(delete("/api/v1/sessions/" + sessionId)));
        } catch (Exception e) {
            log.error("WeKnora deleteSession error", e);
            return Result.error("删除会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result stopSession(String sessionId) {
        try {
            return Result.success(parseWeKnoraBody(post("/api/v1/sessions/" + sessionId + "/stop", Map.of())));
        } catch (Exception e) {
            log.error("WeKnora stopSession error", e);
            return Result.error("停止会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result listKnowledgeBases() {
        try {
            return Result.success(parseWeKnoraBody(get("/api/v1/knowledge-bases")));
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

                String ocrMessage = "";
                String lowerName = fileName.toLowerCase();
                if (lowerName.endsWith(".pdf") && ocrService.needsOcr(fileBytes)) {
                    log.info("Image-based PDF detected: {}, starting OCR...", fileName);
                    String ocrText = ocrService.extractText(fileBytes);
                    if (ocrText != null && !ocrText.isBlank()) {
                        String ocrFileName = fileName.replaceAll("(?i)\\.pdf$", "") + "_OCR.md";
                        Result ocrResult = uploadKnowledgeText(knowledgeBaseId, ocrFileName, ocrText);
                        if (ocrResult.getCode().equals("200")) {
                            ocrMessage = "，已自动识别图片文字";
                        } else {
                            log.warn("OCR text upload failed for: {}", fileName);
                        }
                    }
                }

                return Result.success(parseWeKnoraBody(body), "上传成功" + ocrMessage);
            }
        } catch (Exception e) {
            log.error("WeKnora uploadFile error", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result uploadKnowledgeText(String knowledgeBaseId, String fileName, String textContent) {
        try {
            byte[] textBytes = textContent.getBytes(StandardCharsets.UTF_8);
            RequestBody fileBody = RequestBody.create(textBytes,
                    MediaType.parse("text/markdown; charset=utf-8"));

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
                if (!response.isSuccessful()) return Result.error("文本上传失败: " + body);
                return Result.success(parseWeKnoraBody(body));
            }
        } catch (Exception e) {
            log.error("WeKnora uploadKnowledgeText error", e);
            return Result.error("文本上传失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter knowledgeChat(String sessionId, String query, String knowledgeBaseIds) {
        return streamChat("/api/v1/knowledge-chat/" + sessionId, query, knowledgeBaseIds);
    }

    @Override
    public SseEmitter agentChat(String sessionId, String query, String agentId, String knowledgeBaseIds) {
        return streamChat("/api/v1/agent-chat/" + sessionId, query, knowledgeBaseIds);
    }

    @Override
    public Result createKnowledgeBase(String name, String description) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", name);
            body.put("description", description);
            return Result.success(parseWeKnoraBody(post("/api/v1/knowledge-bases", body)));
        } catch (Exception e) {
            log.error("WeKnora createKnowledgeBase error", e);
            return Result.error("创建知识库失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteKnowledgeBase(String kbId) {
        try {
            return Result.success(parseWeKnoraBody(delete("/api/v1/knowledge-bases/" + kbId)));
        } catch (Exception e) {
            log.error("WeKnora deleteKnowledgeBase error", e);
            return Result.error("删除知识库失败: " + e.getMessage());
        }
    }

    @Override
    public Result listKnowledge(String kbId, int limit, int offset) {
        try {
            String path = "/api/v1/knowledge-bases/" + kbId + "/knowledge?limit=" + limit + "&offset=" + offset;
            return Result.success(parseWeKnoraBody(get(path)));
        } catch (Exception e) {
            log.error("WeKnora listKnowledge error", e);
            return Result.error("获取文档列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteKnowledge(String knowledgeId) {
        try {
            return Result.success(parseWeKnoraBody(delete("/api/v1/knowledge/" + knowledgeId)));
        } catch (Exception e) {
            log.error("WeKnora deleteKnowledge error", e);
            return Result.error("删除文档失败: " + e.getMessage());
        }
    }

    @Override
    public Result updateKnowledgeBase(String kbId, String name, String description) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", name);
            body.put("description", description);
            return Result.success(parseWeKnoraBody(put("/api/v1/knowledge-bases/" + kbId, body)));
        } catch (Exception e) {
            log.error("WeKnora updateKnowledgeBase error", e);
            return Result.error("更新知识库失败: " + e.getMessage());
        }
    }

    @Override
    public Result getKnowledgeContent(String kbId, String knowledgeId) {
        try {
            String fileJson = get("/api/v1/knowledge/" + knowledgeId);
            Map<String, Object> fileMap = JSON.parseObject(fileJson, Map.class);
            Object fileData = fileMap.get("data");
            String title = "";
            if (fileData instanceof Map) {
                title = (String) ((Map<?, ?>) fileData).get("title");
            }
            if (title == null || title.isBlank()) return Result.error("文件不存在");

            Map<String, Object> searchBody = new LinkedHashMap<>();
            searchBody.put("query", title.replaceAll("\\.[^.]+$", "").replaceAll("^.*/", ""));
            searchBody.put("knowledge_base_ids", List.of(kbId));
            String searchResp = post("/api/v1/knowledge-search", searchBody);
            Map<String, Object> searchMap = JSON.parseObject(searchResp, Map.class);
            Object searchData = searchMap.get("data");

            List<Map<String, Object>> chunks = new ArrayList<>();
            if (searchData instanceof List) {
                for (Object item : (List<?>) searchData) {
                    if (item instanceof Map) {
                        Map<String, Object> chunk = (Map<String, Object>) item;
                        Object kid = chunk.get("knowledge_id");
                        if (knowledgeId.equals(kid)) {
                            chunks.add(chunk);
                        }
                    }
                }
            }
            chunks.sort((a, b) -> {
                Object ai = a.getOrDefault("chunk_index", 0);
                Object bi = b.getOrDefault("chunk_index", 0);
                return Integer.compare(
                    ai instanceof Number ? ((Number) ai).intValue() : 0,
                    bi instanceof Number ? ((Number) bi).intValue() : 0);
            });

            return Result.success(chunks);
        } catch (Exception e) {
            log.error("WeKnora getKnowledgeContent error", e);
            return Result.error("获取文件内容失败: " + e.getMessage());
        }
    }

    @Override
    public String chatSync(String sessionId, String query) {
        StringBuilder result = new StringBuilder();
        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("query", query);
            String jsonBody = JSON.toJSONString(bodyMap);

            Request httpRequest = new Request.Builder()
                    .url(config.getBaseUrl() + "/api/v1/knowledge-chat/" + sessionId)
                    .header("X-API-Key", config.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON_TYPE))
                    .build();

            try (Response response = client.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("WeKnora " + response.code());
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            Map<String, Object> parsed = JSON.parseObject(data, Map.class);
                            Object content = parsed.get("content");
                            if (content == null) content = parsed.get("answer");
                            if (content == null) content = parsed.get("text");
                            if (content != null) {
                                result.append(content.toString());
                            }
                        } catch (Exception e) {
                            result.append(data);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("WeKnora chatSync error", e);
            throw new RuntimeException("AI对话失败: " + e.getMessage());
        }
        return result.toString();
    }

    private SseEmitter streamChat(String path, String query, String knowledgeBaseIds) {
        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                Map<String, Object> bodyMap = new LinkedHashMap<>();
                bodyMap.put("query", query);
                if (knowledgeBaseIds != null && !knowledgeBaseIds.isBlank()) {
                    bodyMap.put("knowledge_base_ids", Arrays.asList(knowledgeBaseIds.split(",")));
                }
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
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
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

    private String put(String path, Map<String, Object> bodyMap) throws IOException {
        String jsonBody = JSON.toJSONString(bodyMap);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + path)
                .header("X-API-Key", config.getApiKey())
                .header("Content-Type", "application/json")
                .put(RequestBody.create(jsonBody, JSON_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            String body = response.body().string();
            if (!response.isSuccessful()) throw new IOException("WeKnora " + response.code() + ": " + body);
            return body;
        }
    }
}
