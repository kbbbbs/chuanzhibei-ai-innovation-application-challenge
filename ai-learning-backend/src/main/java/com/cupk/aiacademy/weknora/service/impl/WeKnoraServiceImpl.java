package com.cupk.aiacademy.weknora.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.weknora.config.WeKnoraConfig;
import com.cupk.aiacademy.weknora.dto.WeKnoraSearchRequest;
import com.cupk.aiacademy.weknora.dto.WeKnoraSessionRequest;
import com.cupk.aiacademy.weknora.service.OcrService;
import com.cupk.aiacademy.weknora.service.WeKnoraService;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class WeKnoraServiceImpl implements WeKnoraService {

    private static final Logger log = LoggerFactory.getLogger(WeKnoraServiceImpl.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final WeKnoraConfig config;
    private final OcrService ocrService;

    public WeKnoraServiceImpl(WeKnoraConfig config, OcrService ocrService) {
        this.config = config;
        this.ocrService = ocrService;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    private Map<String, Object> parseWeKnoraBody(String json) {
        return JSON.parseObject(json, Map.class);
    }

    @Override
    public Result search(WeKnoraSearchRequest request) {
        try {
            List<String> knowledgeBaseIds = request.getKnowledgeBaseIds() == null
                    ? Collections.emptyList()
                    : request.getKnowledgeBaseIds().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();

            List<Map<String, Object>> candidates = new ArrayList<>();
            Map<String, Object> base = new LinkedHashMap<>();
            base.put("query", request.getQuery());
            if (request.getLimit() != null && request.getLimit() > 0) {
                base.put("limit", request.getLimit());
            }
            if (request.getMinScore() != null) {
                base.put("min_score", request.getMinScore());
            }

            Map<String, Object> candidate1 = new LinkedHashMap<>(base);
            candidate1.put("knowledge_base_ids", knowledgeBaseIds);
            candidates.add(candidate1);

            if (!knowledgeBaseIds.isEmpty()) {
                Map<String, Object> candidate2 = new LinkedHashMap<>(base);
                candidate2.put("knowledge_base_id", knowledgeBaseIds.get(0));
                candidates.add(candidate2);

                Map<String, Object> candidate3 = new LinkedHashMap<>(base);
                candidate3.put("knowledgeBaseIds", knowledgeBaseIds);
                candidates.add(candidate3);

                Map<String, Object> candidate4 = new LinkedHashMap<>(base);
                candidate4.put("knowledgeBaseId", knowledgeBaseIds.get(0));
                candidates.add(candidate4);
            }

            JSONObject resp = null;
            String[] paths = { "/api/v1/knowledge-search", "/api/v1/knowledge/search" };
            for (String path : paths) {
                for (Map<String, Object> candidate : candidates) {
                    JSONObject current = post(path, candidate);
                    if (current == null) {
                        continue;
                    }
                    Object data = current.get("data");
                    Object items = extractListItems(data);
                    if (items != null) {
                        Object normalized = normalizeKnowledgeSearchResult(items);
                        if (hasNonEmptySearchResults(normalized)) {
                            return Result.success(normalized);
                        }
                    }
                    if (data instanceof JSONArray || data instanceof Collection<?>) {
                        Object normalized = normalizeKnowledgeSearchResult(data);
                        if (hasNonEmptySearchResults(normalized)) {
                            return Result.success(normalized);
                        }
                    }
                    if (isWeKnoraSuccess(current) && data != null) {
                        resp = current;
                    }
                    items = extractListItems(current);
                    if (items != null) {
                        Object normalized = normalizeKnowledgeSearchResult(items);
                        if (hasNonEmptySearchResults(normalized)) {
                            return Result.success(normalized);
                        }
                    }
                }
            }

            Object fallback = searchKnowledgeLocally(knowledgeBaseIds, request.getQuery(), request.getLimit());
            if (hasNonEmptySearchResults(fallback)) {
                return Result.success(fallback);
            }

            if (resp != null) {
                Object data = resp.get("data");
                if (data != null) {
                    return Result.success(normalizeKnowledgeSearchResult(data));
                }
                return Result.success(normalizeKnowledgeSearchResult(resp));
            }
            return Result.error("搜索失败");
        } catch (Exception e) {
            log.error("search failed", e);
            return Result.error("搜索请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result createSession(WeKnoraSessionRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("knowledge_base_id", request.getKnowledgeBaseId());
            if (request.getTitle() != null) body.put("title", request.getTitle());
            if (request.getAgentId() != null) body.put("agent_id", request.getAgentId());
            if (request.getStrategy() != null) body.put("strategy", request.getStrategy());

            JSONObject resp = post("/api/v1/sessions", body);
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success(resp.get("data"));
            }
            return Result.error(resp != null ? resp.getString("message") : "创建会话失败");
        } catch (Exception e) {
            log.error("createSession failed", e);
            return Result.error("创建会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result listSessions() {
        try {
            JSONObject resp = get("/api/v1/sessions");
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success(resp.get("data"));
            }
            return Result.error("获取会话列表失败");
        } catch (Exception e) {
            log.error("listSessions failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteSession(String sessionId) {
        try {
            JSONObject resp = delete("/api/v1/sessions/" + sessionId);
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success();
            }
            return Result.error("删除会话失败");
        } catch (Exception e) {
            log.error("deleteSession failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result stopSession(String sessionId) {
        try {
            JSONObject resp = post("/api/v1/sessions/" + sessionId + "/stop", Map.of());
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success();
            }
            return Result.error("停止会话失败");
        } catch (Exception e) {
            log.error("stopSession failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public SseEmitter knowledgeChat(String sessionId, String query, String knowledgeBaseIds) {
        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query", query);
                if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                    body.put("knowledge_base_ids", Arrays.asList(knowledgeBaseIds.split(",")));
                }

                String jsonBody = JSON.toJSONString(body);
                Request httpRequest = new Request.Builder()
                        .url(config.getBaseUrl() + "/api/v1/knowledge-chat/" + sessionId)
                        .header("X-API-Key", config.getApiKey())
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON_TYPE))
                        .build();

                try (Response response = client.newCall(httpRequest).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        emitter.send(SseEmitter.event().name("error").data("对话失败: HTTP " + response.code()));
                        emitter.complete();
                        return;
                    }

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.body().byteStream(), "UTF-8"));
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
            } catch (Exception e) {
                log.error("knowledgeChat stream error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("对话异常: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @Override
    public SseEmitter agentChat(String sessionId, String query, String agentId, String knowledgeBaseIds) {
        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query", query);
                if (agentId != null) body.put("agentId", agentId);
                if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                    body.put("knowledge_base_ids", Arrays.asList(knowledgeBaseIds.split(",")));
                }

                String jsonBody = JSON.toJSONString(body);
                Request httpRequest = new Request.Builder()
                        .url(config.getBaseUrl() + "/api/v1/agent-chat/" + sessionId)
                        .header("X-API-Key", config.getApiKey())
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON_TYPE))
                        .build();

                try (Response response = client.newCall(httpRequest).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        emitter.send(SseEmitter.event().name("error").data("Agent 对话失败: HTTP " + response.code()));
                        emitter.complete();
                        return;
                    }

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.body().byteStream(), "UTF-8"));
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
            } catch (Exception e) {
                log.error("agentChat stream error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("Agent 对话异常: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @Override
    public Result listKnowledgeBases() {
        try {
            JSONObject resp = get("/api/v1/knowledge-bases");
            if (resp != null && isWeKnoraSuccess(resp)) {
                Object data = resp.get("data");
                Object items = extractListItems(data);
                if (items != null) {
                    return Result.success(items);
                }
                items = extractListItems(resp);
                if (items != null) {
                    return Result.success(items);
                }
                return Result.success(data);
            }
            log.warn("WeKnora listKnowledgeBases unexpected response: {}", resp);
            return Result.error("获取知识库列表失败");
        } catch (Exception e) {
            log.error("listKnowledgeBases failed", e);
            return Result.error("请求失败: " + e.getMessage());
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
                if (response.body() != null) {
                    String body = response.body().string();
                    JSONObject resp = parseWeKnoraResponse(body);
                    String documentId = extractId(resp);

                    if (response.isSuccessful() && documentId != null) {
                        if (ocrService != null && ocrService.needsOcr(fileBytes)) {
                            String ocrText = ocrService.extractText(fileBytes);
                            if (ocrText != null && !ocrText.isBlank()) {
                                uploadKnowledgeText(knowledgeBaseId,
                                        fileName.replaceAll("\\.[^.]+$", "") + "_ocr.md", ocrText);
                            }
                        }
                        Map<String, Object> uploadResult = new LinkedHashMap<>();
                        uploadResult.put("documentId", documentId);
                        uploadResult.put("parseStatus", extractParseStatus(resp, "pending"));
                        uploadResult.put("parseProgress", extractParseProgress(resp));
                        uploadResult.put("errorMessage", extractErrorMessage(resp));
                        return Result.success(uploadResult);
                    }
                    String msg = resp != null ? resp.getString("message") : "上传失败";
                    return Result.error(msg);
                }
                return Result.error("上传失败: 无响应");
            }
        } catch (Exception e) {
            log.error("uploadFile failed", e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result uploadKnowledgeText(String knowledgeBaseId, String fileName, String textContent) {
        try {
            byte[] bytes = textContent.getBytes("UTF-8");
            RequestBody fileBody = RequestBody.create(bytes,
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
                if (response.body() != null) {
                    String body = response.body().string();
                    JSONObject resp = parseWeKnoraResponse(body);
                    if (response.isSuccessful() && resp != null && isWeKnoraSuccess(resp)) {
                        return Result.success(Map.of("documentId", extractId(resp)));
                    }
                    return Result.error(resp != null ? resp.getString("message") : "上传失败");
                }
                return Result.error("上传失败: 无响应");
            }
        } catch (Exception e) {
            log.error("uploadKnowledgeText failed", e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result createKnowledgeBase(String name, String description) {
        try {
            Map<String, Object> body = Map.of("name", name, "description", description);
            JSONObject resp = post("/api/v1/knowledge-bases", body);
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success(resp.get("data"));
            }
            return Result.error(resp != null ? resp.getString("message") : "创建知识库失败");
        } catch (Exception e) {
            log.error("createKnowledgeBase failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteKnowledgeBase(String kbId) {
        try {
            JSONObject resp = delete("/api/v1/knowledge-bases/" + kbId);
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success();
            }
            return Result.error("删除知识库失败");
        } catch (Exception e) {
            log.error("deleteKnowledgeBase failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result listKnowledge(String kbId, int limit, int offset) {
        try {
            JSONObject resp = get("/api/v1/knowledge-bases/" + kbId + "/knowledge?limit=" + limit + "&offset=" + offset);
            if (resp != null && isWeKnoraSuccess(resp)) {
                Object data = resp.get("data");
                Object items = extractListItems(data);
                if (data instanceof JSONObject) {
                    JSONObject dataObj = (JSONObject) data;
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("items", normalizeKnowledgeItems(items != null ? items : data));
                    result.put("total", dataObj.getOrDefault("total", limit));
                    result.put("offset", offset);
                    return Result.success(result);
                }
                if (items != null) {
                    return Result.success(normalizeKnowledgeItems(items));
                }
                return Result.success(normalizeKnowledgeItems(data));
            }
            return Result.error("获取知识文档列表失败");
        } catch (Exception e) {
            log.error("listKnowledge failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteKnowledge(String knowledgeId) {
        try {
            JSONObject resp = delete("/api/v1/knowledge/" + knowledgeId);
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success();
            }
            return Result.error("删除知识文档失败");
        } catch (Exception e) {
            log.error("deleteKnowledge failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result updateKnowledgeBase(String kbId, String name, String description) {
        try {
            Map<String, Object> body = Map.of("name", name, "description", description);
            JSONObject resp = put("/api/v1/knowledge-bases/" + kbId, body);
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success(resp.get("data"));
            }
            return Result.error("更新知识库失败");
        } catch (Exception e) {
            log.error("updateKnowledgeBase failed", e);
            return Result.error("请求失败: " + e.getMessage());
        }
    }

    @Override
    public Result getKnowledgeGraph(String kbId) {
        try {
            JSONObject resp = get("/api/v1/knowledge-bases/" + kbId);
            JSONObject knowledgeBase = extractKnowledgeBaseObject(resp, kbId);

            if (knowledgeBase == null) {
                JSONObject listResp = get("/api/v1/knowledge-bases");
                knowledgeBase = findKnowledgeBaseFromList(listResp, kbId);
            }

            if (isProbabilityKnowledgeBase(kbId, knowledgeBase)) {
                return Result.success(buildProbabilityGraph(kbId));
            }

            return Result.success(normalizeKnowledgeGraph(kbId, knowledgeBase));
        } catch (Exception e) {
            log.error("getKnowledgeGraph failed", e);
            return Result.error("获取知识图谱失败: " + e.getMessage());
        }
    }

    @Override
    public Result getKnowledgeContent(String kbId, String knowledgeId) {
        try {
            JSONObject resp = get("/api/v1/knowledge/" + knowledgeId + "/content");
            if (resp != null && isWeKnoraSuccess(resp)) {
                Object data = resp.get("data");
                if (data != null) {
                    Object chunks = extractContentChunks(data);
                    if (chunks != null) {
                        return Result.success(chunks);
                    }
                    return Result.success(data);
                }
                return Result.error("文档内容为空");
            }
            return tryGetChunks(knowledgeId);
        } catch (Exception e) {
            log.warn("getKnowledgeContent failed, trying fallback: {}", e.getMessage());
            return tryGetChunks(knowledgeId);
        }
    }

    private boolean isProbabilityKnowledgeBase(String kbId, JSONObject knowledgeBase) {
        if ("probability".equalsIgnoreCase(kbId) || "概率论".equals(kbId)) {
            return true;
        }
        if (knowledgeBase == null) {
            return false;
        }
        String name = knowledgeBase.getString("name");
        return "概率论".equals(name) || "Probability".equalsIgnoreCase(name);
    }

    private Map<String, Object> buildProbabilityGraph(String kbId) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("knowledgeBaseId", kbId);
        graph.put("enabled", true);

        List<Map<String, Object>> nodes = List.of(
            Map.of("id", "probability", "label", "概率论"),
            Map.of("id", "event", "label", "随机事件"),
            Map.of("id", "probability_basic", "label", "概率基础"),
            Map.of("id", "random_variable", "label", "随机变量"),
            Map.of("id", "multivariate_rv", "label", "多维随机变量"),
            Map.of("id", "numerical_characteristics", "label", "数字特征"),
            Map.of("id", "distributions", "label", "重要分布"),
            Map.of("id", "limit_theorems", "label", "大数定律与中心极限定理"),
            Map.of("id", "sample_space", "label", "样本空间"),
            Map.of("id", "conditional_probability", "label", "条件概率"),
            Map.of("id", "bayes", "label", "贝叶斯公式"),
            Map.of("id", "expectation", "label", "数学期望"),
            Map.of("id", "variance", "label", "方差"),
            Map.of("id", "normal", "label", "正态分布"),
            Map.of("id", "binomial", "label", "二项分布"),
            Map.of("id", "poisson", "label", "泊松分布"),
            Map.of("id", "clt", "label", "中心极限定理")
        );

        List<Map<String, Object>> relations = List.of(
            Map.of("source", "probability", "target", "event", "label", "包含"),
            Map.of("source", "probability", "target", "probability_basic", "label", "包含"),
            Map.of("source", "probability", "target", "random_variable", "label", "包含"),
            Map.of("source", "probability", "target", "multivariate_rv", "label", "包含"),
            Map.of("source", "probability", "target", "numerical_characteristics", "label", "包含"),
            Map.of("source", "probability", "target", "distributions", "label", "包含"),
            Map.of("source", "probability", "target", "limit_theorems", "label", "包含"),
            Map.of("source", "event", "target", "sample_space", "label", "组成"),
            Map.of("source", "probability_basic", "target", "conditional_probability", "label", "组成"),
            Map.of("source", "conditional_probability", "target", "bayes", "label", "推导"),
            Map.of("source", "random_variable", "target", "expectation", "label", "可计算"),
            Map.of("source", "random_variable", "target", "variance", "label", "可计算"),
            Map.of("source", "distributions", "target", "binomial", "label", "包含"),
            Map.of("source", "distributions", "target", "poisson", "label", "包含"),
            Map.of("source", "distributions", "target", "normal", "label", "包含"),
            Map.of("source", "limit_theorems", "target", "clt", "label", "组成")
        );

        graph.put("nodes", nodes);
        graph.put("relations", relations);
        return graph;
    }

    private JSONObject extractKnowledgeBaseObject(JSONObject resp, String kbId) {
        if (resp == null || !isWeKnoraSuccess(resp)) return null;
        Object data = resp.get("data");
        if (data instanceof JSONObject dataObj) {
            String id = dataObj.getString("id");
            if (id == null || kbId.equals(id)) {
                return dataObj;
            }
        }
        return null;
    }

    private JSONObject findKnowledgeBaseFromList(JSONObject resp, String kbId) {
        if (resp == null || !isWeKnoraSuccess(resp)) return null;
        Object data = resp.get("data");
        Object items = extractListItems(data);
        if (!(items instanceof JSONArray array)) return null;
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (item instanceof JSONObject obj && kbId.equals(obj.getString("id"))) {
                return obj;
            }
        }
        return null;
    }

    private Map<String, Object> normalizeKnowledgeGraph(String kbId, JSONObject knowledgeBase) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("knowledgeBaseId", kbId);
        graph.put("enabled", false);
        graph.put("nodes", Collections.emptyList());
        graph.put("relations", Collections.emptyList());

        if (knowledgeBase == null) {
            return graph;
        }

        Object rawConfig = knowledgeBase.get("extract_config");
        if (rawConfig == null) rawConfig = knowledgeBase.get("extractConfig");
        JSONObject config = toJsonObject(rawConfig);
        if (config == null) {
            return graph;
        }

        List<Object> nodes = toPlainList(config.get("nodes"));
        List<Object> relations = toPlainList(config.get("relations"));

        Boolean enabled = config.getBoolean("enabled");
        if (enabled == null) {
            enabled = !nodes.isEmpty() || !relations.isEmpty();
        }

        graph.put("enabled", enabled);
        graph.put("nodes", nodes);
        graph.put("relations", relations);
        return graph;
    }

    private Object normalizeKnowledgeItems(Object source) {
        if (source instanceof JSONArray array) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : array) {
                normalized.add(normalizeKnowledgeItem(item));
            }
            return normalized;
        }
        if (source instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : collection) {
                normalized.add(normalizeKnowledgeItem(item));
            }
            return normalized;
        }
        return source;
    }

    private Object normalizeKnowledgeSearchResult(Object source) {
        Object items = extractListItems(source);
        if (items != null) {
            return normalizeKnowledgeItems(items);
        }
        return normalizeKnowledgeItems(source);
    }

    private Object normalizeKnowledgeItem(Object item) {
        JSONObject obj = toJsonObject(item);
        if (obj == null) {
            return item;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue());
        }
        normalized.put("parseStatus", extractParseStatus(obj, "success"));
        normalized.put("parseProgress", extractParseProgress(obj));
        normalized.put("errorMessage", extractErrorMessage(obj));
        return normalized;
    }

    private String extractParseStatus(JSONObject obj, String defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        String raw = firstNonBlank(
                obj.getString("parseStatus"),
                obj.getString("parse_status"),
                obj.getString("processingStatus"),
                obj.getString("processing_status"),
                obj.getString("status")
        );
        if (raw == null) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("fail") || normalized.contains("error")) return "failed";
        if (normalized.contains("pend") || normalized.contains("queue") || normalized.contains("wait")) return "pending";
        if (normalized.contains("process") || normalized.contains("parse") || normalized.contains("index") || normalized.contains("running")) return "parsing";
        if (normalized.contains("success") || normalized.contains("done") || normalized.contains("finish") || normalized.contains("complete")) return "success";
        return defaultValue;
    }

    private int extractParseProgress(JSONObject obj) {
        if (obj == null) {
            return 0;
        }
        Integer direct = parseInteger(firstNonBlank(
                stringify(obj.get("parseProgress")),
                stringify(obj.get("parse_progress")),
                stringify(obj.get("progress")),
                stringify(obj.get("processingProgress"))
        ));
        if (direct == null) {
            String status = extractParseStatus(obj, "pending");
            return "success".equals(status) ? 100 : 0;
        }
        return Math.max(0, Math.min(100, direct));
    }

    private String extractErrorMessage(JSONObject obj) {
        if (obj == null) {
            return "";
        }
        String message = firstNonBlank(
                obj.getString("errorMessage"),
                obj.getString("error_message"),
                obj.getString("message"),
                obj.getString("msg"),
                obj.getString("error")
        );
        return message == null ? "" : message;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String normalized = value.trim().replace("%", "");
            if (normalized.contains(".")) {
                return (int) Math.round(Double.parseDouble(normalized));
            }
            return Integer.parseInt(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasNonEmptySearchResults(Object value) {
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof JSONArray array) {
            return !array.isEmpty();
        }
        return value != null;
    }

    private Object searchKnowledgeLocally(List<String> knowledgeBaseIds, String query, Integer limit) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        int maxResults = limit != null && limit > 0 ? limit : 12;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> results = new ArrayList<>();
        for (String kbId : knowledgeBaseIds) {
            if (kbId == null || kbId.isBlank()) {
                continue;
            }
            try {
                JSONObject resp = get("/api/v1/knowledge-bases/" + kbId + "/knowledge?limit=100&offset=0");
                if (resp == null || !isWeKnoraSuccess(resp)) {
                    continue;
                }
                Object data = resp.get("data");
                Object items = extractListItems(data);
                if (!(items instanceof JSONArray array)) {
                    continue;
                }
                for (int i = 0; i < array.size() && results.size() < maxResults; i++) {
                    JSONObject item = array.getJSONObject(i);
                    String knowledgeId = firstNonBlank(item.getString("id"), item.getString("knowledge_id"));
                    if (knowledgeId == null) {
                        continue;
                    }
                    Result chunksResult = tryGetChunks(knowledgeId);
                    if (!"200".equals(chunksResult.getCode())) {
                        continue;
                    }
                    Object chunkData = chunksResult.getData();
                    List<Object> chunks = toPlainList(chunkData);
                    for (Object chunk : chunks) {
                        String text = extractSearchableText(chunk);
                        if (text == null || text.isBlank()) {
                            continue;
                        }
                        String lower = text.toLowerCase(Locale.ROOT);
                        int index = lower.indexOf(needle);
                        if (index < 0) {
                            continue;
                        }
                        Map<String, Object> match = new LinkedHashMap<>();
                        match.put("knowledge_id", knowledgeId);
                        match.put("knowledge_title", firstNonBlank(item.getString("title"), item.getString("name"), item.getString("file_name"), knowledgeId));
                        match.put("file_name", firstNonBlank(item.getString("file_name"), item.getString("name"), item.getString("title")));
                        match.put("content", buildSnippet(text, index, needle.length()));
                        match.put("score", 1.0);
                        results.add(match);
                        if (results.size() >= maxResults) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("local knowledge fallback search failed for kb {}: {}", kbId, e.getMessage());
            }
        }
        return results;
    }

    private String extractSearchableText(Object chunk) {
        JSONObject obj = toJsonObject(chunk);
        if (obj == null) {
            return chunk == null ? "" : String.valueOf(chunk);
        }
        return firstNonBlank(
                obj.getString("text"),
                obj.getString("content"),
                obj.getString("chunk_text"),
                obj.getString("chunk_content"),
                obj.getString("document")
        );
    }

    private String buildSnippet(String text, int hitIndex, int keywordLength) {
        int start = Math.max(0, hitIndex - 80);
        int end = Math.min(text.length(), hitIndex + keywordLength + 120);
        String snippet = text.substring(start, end).trim();
        if (start > 0) snippet = "…" + snippet;
        if (end < text.length()) snippet = snippet + "…";
        return snippet;
    }

    private JSONObject toJsonObject(Object value) {
        if (value instanceof JSONObject obj) return obj;
        if (value instanceof Map<?, ?> map) return new JSONObject(new LinkedHashMap<>(map));
        if (value instanceof String str && !str.isBlank()) {
            try {
                Object parsed = JSON.parse(str);
                if (parsed instanceof JSONObject obj) return obj;
                if (parsed instanceof Map<?, ?> map) return new JSONObject(new LinkedHashMap<>(map));
            } catch (Exception e) {
                log.warn("parse graph config failed: {}", e.getMessage());
            }
        }
        return null;
    }

    private List<Object> toPlainList(Object value) {
        if (value instanceof JSONArray array) {
            return new ArrayList<>(array);
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return Collections.emptyList();
    }

    private Object extractContentChunks(Object data) {
        if (data instanceof JSONArray) {
            return data;
        }
        if (!(data instanceof JSONObject)) {
            return null;
        }
        JSONObject dataObj = (JSONObject) data;
        Object chunks = dataObj.get("chunks");
        if (chunks == null) chunks = dataObj.get("items");
        if (chunks == null) chunks = dataObj.get("list");
        if (chunks != null) return chunks;

        Object document = dataObj.get("document");
        if (document == null) document = dataObj.get("content");
        if (document == null) document = dataObj.get("text");
        if (document == null) return null;

        if (document instanceof String) {
            String text = (String) document;
            List<Map<String, String>> chunkList = new ArrayList<>();
            for (String para : text.split("\\n\\n+")) {
                if (!para.trim().isEmpty()) {
                    chunkList.add(Map.of("text", para.trim()));
                }
            }
            return chunkList;
        }
        return document;
    }

    private Result tryGetChunks(String knowledgeId) {
        try {
            JSONObject resp = get("/api/v1/knowledge/" + knowledgeId + "/chunks");
            if (resp != null && isWeKnoraSuccess(resp)) {
                return Result.success(resp.get("data"));
            }
            return Result.error("获取文档内容失败");
        } catch (Exception e) {
            return Result.error("网络请求失败: " + e.getMessage());
        }
    }

    private Result searchByTitle(String kbId, String title) {
        try {
            JSONObject resp = get("/api/v1/knowledge/" + kbId + "/knowledge?limit=100&offset=0");
            if (resp != null && isWeKnoraSuccess(resp)) {
                Object data = resp.get("data");
                List<Object> matched = new ArrayList<>();
                if (data instanceof JSONObject) {
                    JSONObject dataObj = (JSONObject) data;
                    JSONArray items = dataObj.getJSONArray("items");
                    if (items == null) items = dataObj.getJSONArray("list");
                    if (items == null) items = dataObj.getJSONArray("documents");
                    if (items != null) {
                        for (int i = 0; i < items.size(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            String name = item.getString("name");
                            if (name == null) name = item.getString("title");
                            if (name != null && name.contains(title)) {
                                matched.add(item);
                            }
                        }
                    }
                }
                return Result.success(matched);
            }
            return Result.error("搜索失败");
        } catch (Exception e) {
            return Result.error("搜索请求失败: " + e.getMessage());
        }
    }

    private void collectChunks(String text, String separator, String prefix,
                               List<Map<String, String>> result, Set<String> seen) {
        if (text == null || text.isBlank()) return;
        String[] parts = text.split(separator);
        for (int i = 0; i < parts.length; i++) {
            String chunk = parts[i].trim();
            if (!chunk.isEmpty() && !seen.contains(chunk)) {
                seen.add(chunk);
                Map<String, String> m = new LinkedHashMap<>();
                m.put("text", (prefix.isEmpty() ? "" : prefix + " ") + chunk);
                m.put("index", String.valueOf(result.size()));
                result.add(m);
            }
        }
    }

    @Override
    public void downloadFile(String knowledgeId, HttpServletResponse response) {
        java.io.File localFile = findLocalFile(knowledgeId);
        if (localFile != null) {
            streamLocalFile(localFile, response);
            return;
        }

        String publicUrl = getPublicFileUrl(knowledgeId);
        if (publicUrl != null) {
            try {
                response.sendRedirect(publicUrl);
                return;
            } catch (IOException e) {
                log.warn("redirect failed, trying WeKnora download");
            }
        }

        try {
            Request httpRequest = new Request.Builder()
                    .url(config.getBaseUrl() + "/api/v1/knowledge/" + knowledgeId + "/file")
                    .header("X-API-Key", config.getApiKey())
                    .get()
                    .build();

            try (okhttp3.Response weknoraResp = client.newCall(httpRequest).execute()) {
                if (weknoraResp.isSuccessful() && weknoraResp.body() != null) {
                    byte[] fileBytes = weknoraResp.body().bytes();
                    saveLocalFile(knowledgeId, knowledgeId, fileBytes);
                    localFile = findLocalFile(knowledgeId);
                    if (localFile != null) {
                        streamLocalFile(localFile, response);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("WeKnora download fallback failed for {}: {}", knowledgeId, e.getMessage());
        }

        try {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在，请重新上传");
        } catch (IOException ignored) {}
    }

    private void streamLocalFile(java.io.File localFile, HttpServletResponse response) {
        try {
            String fileName = localFile.getName();
            response.setContentType(inferContentType(fileName, ""));
            response.setHeader("Content-Disposition",
                    "inline; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8") + "\"");
            response.setContentLengthLong(localFile.length());

            try (InputStream in = new java.io.FileInputStream(localFile);
                 OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
        } catch (Exception e) {
            log.error("streamLocalFile error: {}", e.getMessage());
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件读取失败");
            } catch (IOException ignored) {}
        }
    }

    private String extractId(Object weKnoraData) {
        if (weKnoraData instanceof JSONObject obj) {
            Object data = obj.get("data");
            String nestedId = extractId(data);
            if (nestedId != null) {
                return nestedId;
            }
            Object id = obj.get("id");
            return id != null ? String.valueOf(id) : null;
        }
        if (weKnoraData instanceof Map) {
            Object id = ((Map<?, ?>) weKnoraData).get("id");
            return id != null ? String.valueOf(id) : null;
        }
        return null;
    }

    private void saveLocalFile(String knowledgeId, String originalName, byte[] bytes) {
        try {
            java.io.File dir = new java.io.File(config.getStoragePath());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String ext = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) ext = originalName.substring(dot).toLowerCase();
            java.io.File file = new java.io.File(dir, knowledgeId + ext);
            java.nio.file.Files.write(file.toPath(), bytes);
            log.info("Saved local file: {}", file.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to save local file for {}: {}", knowledgeId, e.getMessage());
        }
    }

    private java.io.File findLocalFile(String knowledgeId) {
        java.io.File dir = new java.io.File(config.getStoragePath());
        if (!dir.exists()) return null;
        java.io.File[] files = dir.listFiles((d, name) -> name.startsWith(knowledgeId + "."));
        if (files == null || files.length == 0) {
            files = dir.listFiles((d, name) -> name.startsWith(knowledgeId + "_"));
        }
        return files != null && files.length > 0 ? files[0] : null;
    }

    private String getPublicFileUrl(String knowledgeId) {
        java.io.File f = findLocalFile(knowledgeId);
        if (f == null) return null;
        return "/uploads/knowledge/" + f.getName();
    }

    private String inferContentType(String title, String fileType) {
        String t = (title + " " + fileType).toLowerCase();
        if (t.contains(".pdf") || t.contains("pdf")) return "application/pdf";
        if (t.contains(".doc") || t.contains("word")) return "application/msword";
        if (t.contains(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (t.contains(".ppt") || t.contains("powerpoint")) return "application/vnd.ms-powerpoint";
        if (t.contains(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.document";
        if (t.contains(".jpg") || t.contains(".jpeg")) return "image/jpeg";
        if (t.contains(".png")) return "image/png";
        if (t.contains(".txt") || t.contains(".md")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    // ===== 修改后的 proxyLocalFile：直接访问本地文件，不再通过 HTTP 代理 =====
    @Override
    public void proxyLocalFile(String path, HttpServletResponse response) {
        try {
            String fileName = path.contains("/")
                    ? path.substring(path.lastIndexOf('/') + 1)
                    : path;

            log.debug("Proxying local file: {}", fileName);

            java.io.File dir = new java.io.File(config.getStoragePath());
            if (!dir.exists()) {
                log.warn("Storage directory does not exist: {}", config.getStoragePath());
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Storage directory not found");
                return;
            }

            java.io.File[] files = dir.listFiles((d, name) -> name.equals(fileName));

            if (files == null || files.length == 0) {
                log.warn("File not found in storage: {}", fileName);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found: " + fileName);
                return;
            }

            java.io.File targetFile = files[0];
            streamLocalFile(targetFile, response);

        } catch (Exception e) {
            log.error("proxyLocalFile '{}' failed: {}", path, e.getMessage(), e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "File access error");
            } catch (IOException ignored) {}
        }
    }

    private void tryLocalFileDirect(String url, HttpServletResponse response) {
        try {
            Request httpRequest = new Request.Builder().url(url).get().build();
            try (okhttp3.Response resp = client.newCall(httpRequest).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                streamResponse(resp, response);
            }
        } catch (Exception e) {
            try { response.sendError(HttpServletResponse.SC_NOT_FOUND); } catch (IOException ignored) {}
        }
    }

    private void streamResponse(okhttp3.Response source, HttpServletResponse target) throws IOException {
        String contentType = source.header("Content-Type");
        if (contentType != null) target.setContentType(contentType);
        target.setHeader("Cache-Control", "public, max-age=3600");
        try (InputStream in = source.body().byteStream();
             OutputStream out = target.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
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
                        new InputStreamReader(response.body().byteStream(), "UTF-8"));
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
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query", query);
                if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                    body.put("knowledge_base_ids", Arrays.asList(knowledgeBaseIds.split(",")));
                }

                String jsonBody = JSON.toJSONString(body);
                Request httpRequest = new Request.Builder()
                        .url(config.getBaseUrl() + path)
                        .header("X-API-Key", config.getApiKey())
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, JSON_TYPE))
                        .build();

                try (Response response = client.newCall(httpRequest).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        emitter.send(SseEmitter.event().name("error")
                                .data("对话失败: HTTP " + response.code()));
                        emitter.complete();
                        return;
                    }

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.body().byteStream(), "UTF-8"));
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
            } catch (Exception e) {
                log.error("streamChat error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("对话异常: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private JSONObject get(String apiPath) {
        try {
            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + apiPath)
                    .header("X-API-Key", config.getApiKey())
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.body() == null) return null;
                String body = response.body().string();
                log.debug("WeKnora GET {} -> {}", apiPath, body.length() > 500 ? body.substring(0, 500) + "..." : body);
                return parseWeKnoraResponse(body);
            }
        } catch (Exception e) {
            log.warn("GET {} failed: {}", apiPath, e.getMessage());
            return null;
        }
    }

    private Object extractListItems(Object data) {
        if (!(data instanceof JSONObject)) return data instanceof JSONArray ? data : null;
        JSONObject dataObj = (JSONObject) data;
        Object items = dataObj.get("items");
        if (items == null) items = dataObj.get("list");
        if (items == null) items = dataObj.get("knowledgeBases");
        if (items == null) items = dataObj.get("documents");
        if (items == null) items = dataObj.get("records");
        if (items == null) items = dataObj.get("rows");
        if (items == null) items = dataObj.get("results");
        return items;
    }

    /**
     * WeKnora API may return success in several formats:
     *   {"code":"200", ...}  or  {"code":200, ...}  or  {"success":true, ...}  or just {"data":...}
     */
    private boolean isWeKnoraSuccess(JSONObject resp) {
        if (resp == null) return false;
        Boolean success = resp.getBoolean("success");
        if (success != null) return success;
        String codeStr = resp.getString("code");
        if (codeStr != null) return "200".equals(codeStr) || "0".equals(codeStr);
        Integer codeInt = resp.getInteger("code");
        if (codeInt != null) return codeInt == 200 || codeInt == 0;
        return resp.containsKey("data");
    }

    /**
     * Parse WeKnora response body. Handles both JSON objects and arrays
     * (some endpoints return a bare array instead of {data: [...]}).
     */
    private JSONObject parseWeKnoraResponse(String body) {
        if (body == null || body.isBlank()) return null;
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        Object parsed = JSON.parse(trimmed);
        if (parsed instanceof JSONObject) {
            return (JSONObject) parsed;
        }
        if (parsed instanceof JSONArray) {
            JSONObject wrapper = new JSONObject();
            wrapper.put("data", parsed);
            return wrapper;
        }
        return null;
    }

    private JSONObject post(String apiPath, Map<String, Object> bodyMap) {
        try {
            String jsonBody = JSON.toJSONString(bodyMap);
            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + apiPath)
                    .header("X-API-Key", config.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.body() == null) return null;
                String body = response.body().string();
                return parseWeKnoraResponse(body);
            }
        } catch (Exception e) {
            log.warn("POST {} failed: {}", apiPath, e.getMessage());
            return null;
        }
    }

    private JSONObject put(String apiPath, Map<String, Object> bodyMap) {
        try {
            String jsonBody = JSON.toJSONString(bodyMap);
            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + apiPath)
                    .header("X-API-Key", config.getApiKey())
                    .header("Content-Type", "application/json")
                    .put(RequestBody.create(jsonBody, JSON_TYPE))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.body() == null) return null;
                String body = response.body().string();
                return parseWeKnoraResponse(body);
            }
        } catch (Exception e) {
            log.warn("PUT {} failed: {}", apiPath, e.getMessage());
            return null;
        }
    }

    private JSONObject delete(String apiPath) {
        try {
            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + apiPath)
                    .header("X-API-Key", config.getApiKey())
                    .delete()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.body() == null) return null;
                String body = response.body().string();
                return parseWeKnoraResponse(body);
            }
        } catch (Exception e) {
            log.warn("DELETE {} failed: {}", apiPath, e.getMessage());
            return null;
        }
    }
}
