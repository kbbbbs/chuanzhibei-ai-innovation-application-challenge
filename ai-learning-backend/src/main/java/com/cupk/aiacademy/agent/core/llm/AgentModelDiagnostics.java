package com.cupk.aiacademy.agent.core.llm;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sanitized model failure details for logs, health checks and Agent API data.
 */
public class AgentModelDiagnostics {
    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";

    private final String provider;
    private final String model;
    private final String category;
    private final String rootException;
    private final String rootMessage;
    private final boolean retryable;
    private final LocalDateTime timestamp;

    private AgentModelDiagnostics(String provider,
                                  String model,
                                  String category,
                                  String rootException,
                                  String rootMessage,
                                  boolean retryable,
                                  LocalDateTime timestamp) {
        this.provider = blankToUnknown(provider);
        this.model = blankToUnknown(model);
        this.category = blankToUnknown(category);
        this.rootException = blankToUnknown(rootException);
        this.rootMessage = sanitize(rootMessage);
        this.retryable = retryable;
        this.timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
    }

    public static AgentModelDiagnostics emptyResponse(String provider, String model) {
        return new AgentModelDiagnostics(provider,
                model,
                EMPTY_RESPONSE,
                EMPTY_RESPONSE,
                "Model returned an empty response.",
                true,
                LocalDateTime.now());
    }

    public static AgentModelDiagnostics from(String provider, String model, Throwable throwable) {
        Throwable root = rootCause(throwable);
        String message = root == null ? "" : root.getMessage();
        String category = classify(root, message);
        return new AgentModelDiagnostics(provider,
                model,
                category,
                root == null ? "" : root.getClass().getName(),
                message,
                isRetryable(category),
                LocalDateTime.now());
    }

    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", provider);
        data.put("model", model);
        data.put("category", category);
        data.put("rootException", rootException);
        data.put("rootMessage", rootMessage);
        data.put("retryable", retryable);
        data.put("timestamp", timestamp);
        return data;
    }

    /**
     * 面向普通聊天接口的安全诊断信息。原始异常类和错误正文只保留在服务端日志及管理员健康检查中。
     */
    public Map<String, Object> toPublicData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", category);
        data.put("retryable", retryable);
        data.put("timestamp", timestamp);
        return data;
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public String category() {
        return category;
    }

    public String rootMessage() {
        return rootMessage;
    }

    public boolean retryable() {
        return retryable;
    }

    private static String classify(Throwable root, String message) {
        if (root instanceof com.cupk.aiacademy.model.ModelConfigurationException config) return config.code();
        if (root instanceof com.cupk.aiacademy.model.ModelInvocationException invocation) return invocation.code();
        String value = (message == null ? "" : message).toLowerCase(Locale.ROOT);
        if (containsAny(value, "401", "unauthorized", "invalid api key", "api key", "authentication")) {
            return "AUTHENTICATION";
        }
        if (containsAny(value, "403", "forbidden", "permission", "insufficient")) {
            return "PERMISSION";
        }
        if (containsAny(value, "429", "rate limit", "too many requests", "quota")) {
            return "RATE_LIMIT";
        }
        if (containsAny(value, "400", "bad request", "invalid request", "model not found", "invalid model")) {
            return "INVALID_REQUEST";
        }
        if (root instanceof SocketTimeoutException || containsAny(value, "timeout", "timed out")) {
            return "TIMEOUT";
        }
        if (root instanceof UnknownHostException
                || root instanceof ConnectException
                || containsAny(value, "connection refused", "connect", "unknown host", "ssl", "network")) {
            return "NETWORK";
        }
        if (containsAny(value, "500", "502", "503", "504", "internal server", "bad gateway", "service unavailable")) {
            return "PROVIDER_ERROR";
        }
        return "UNKNOWN";
    }

    private static boolean isRetryable(String category) {
        return "RATE_LIMIT".equals(category)
                || "TIMEOUT".equals(category)
                || "NETWORK".equals(category)
                || "PROVIDER_ERROR".equals(category)
                || EMPTY_RESPONSE.equals(category)
                || "UNKNOWN".equals(category);
    }

    private static Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean containsAny(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message
                .replaceAll("(?i)(api[-_ ]?key\\s*[=:]\\s*)[^\\s,;]+", "$1***")
                .replaceAll("(?i)(authorization:\\s*bearer\\s+)[^\\s,;]+", "$1***")
                .replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-***")
                .trim();
    }
}
