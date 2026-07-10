package com.cupk.aiacademy.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.mapper.OperationLogMapper;
import com.cupk.aiacademy.pojo.OperationLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class UsageLogInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(UsageLogInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = UsageLogInterceptor.class.getName() + ".startTime";
    private static final String SKIP_ATTRIBUTE = UsageLogInterceptor.class.getName() + ".skip";
    private static final String REDACTED = "[REDACTED]";

    private final OperationLogMapper operationLogMapper;

    public UsageLogInterceptor(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldSkip(request)) {
            request.setAttribute(SKIP_ATTRIBUTE, true);
            return true;
        }
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(SKIP_ATTRIBUTE)) || shouldSkip(request)) {
            return;
        }

        try {
            OperationLog usageLog = new OperationLog();
            usageLog.setUserId(UserContext.getCurrentUserId());
            usageLog.setOperation("usage.request");
            usageLog.setMethod(request.getMethod());
            usageLog.setUrl(request.getRequestURI());
            usageLog.setParams(buildParams(request, response));
            usageLog.setIp(resolveClientIp(request));
            usageLog.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(usageLog);
        } catch (Exception loggingException) {
            log.warn("Failed to write usage log for {} {}", request.getMethod(), request.getRequestURI(), loggingException);
        }
    }

    private String buildParams(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> params = new LinkedHashMap<>();
        long durationMs = durationMs(request);
        boolean fileUpload = isFileUpload(request);

        if (!fileUpload) {
            params.put("query", safeQueryString(request.getQueryString()));
        }
        params.put("status", response.getStatus());
        params.put("durationMs", durationMs);
        if (!fileUpload) {
            params.put("contentType", request.getContentType());
            long contentLength = request.getContentLengthLong();
            params.put("contentLength", contentLength >= 0 ? contentLength : null);
        }
        params.put("summary", request.getMethod() + " " + request.getRequestURI());
        if (!fileUpload) {
            params.put("userAgent", request.getHeader("User-Agent"));
        }

        return JSON.toJSONString(params, JSONWriter.Feature.WriteMapNullValue);
    }

    private long durationMs(HttpServletRequest request) {
        Object start = request.getAttribute(START_TIME_ATTRIBUTE);
        if (!(start instanceof Long startTime)) {
            return 0L;
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
    }

    private String safeQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        return Arrays.stream(queryString.split("&", -1))
                .map(this::redactQueryPart)
                .collect(Collectors.joining("&"));
    }

    private String redactQueryPart(String queryPart) {
        if (queryPart == null || queryPart.isEmpty()) {
            return queryPart;
        }
        int separatorIndex = queryPart.indexOf('=');
        String key = separatorIndex >= 0 ? queryPart.substring(0, separatorIndex) : queryPart;
        if (!isSensitiveKey(key)) {
            return queryPart;
        }
        return key + "=" + REDACTED;
    }

    private boolean isSensitiveKey(String encodedKey) {
        String key = decode(encodedKey).toLowerCase(Locale.ROOT);
        return key.contains("authorization")
                || key.contains("password")
                || key.contains("token")
                || key.contains("secret")
                || "pwd".equals(key);
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isFileUpload(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = pathWithoutContext(request);
        return startsWithSegment(path, "/operation-logs")
                || startsWithSegment(path, "/uploads")
                || startsWithSegment(path, "/media")
                || path.startsWith("/api/weknora/local-file/")
                || isStaticResource(path);
    }

    private String pathWithoutContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private boolean startsWithSegment(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private boolean isStaticResource(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.equals("/favicon.ico")
                || lowerPath.startsWith("/assets/")
                || lowerPath.startsWith("/static/")
                || lowerPath.startsWith("/webjars/")
                || lowerPath.matches(".*\\.(css|js|map|ico|png|jpg|jpeg|gif|svg|webp|woff|woff2|ttf|eot)$");
    }
}
