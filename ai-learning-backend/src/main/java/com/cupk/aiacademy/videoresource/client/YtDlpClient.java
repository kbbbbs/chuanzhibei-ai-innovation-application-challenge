package com.cupk.aiacademy.videoresource.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;

@Component
public class YtDlpClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public YtDlpClient(ObjectMapper objectMapper,
                       @Value("${ytdlp.base-url:http://127.0.0.1:8091}") String baseUrl,
                       @Value("${ytdlp.timeout-seconds:1800}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    public DownloadResponse download(String url, String outputDir, Long resourceId) throws IOException {
        DownloadRequest payload = new DownloadRequest();
        payload.setUrl(url);
        payload.setOutputDir(outputDir);
        payload.setResourceId(resourceId);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/ytdlp/download")
                .post(RequestBody.create(objectMapper.writeValueAsBytes(payload), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(body.isBlank() ? "yt-dlp 服务调用失败" : body);
            }
            return objectMapper.readValue(body, DownloadResponse.class);
        } catch (ConnectException e) {
            throw new IOException("无法连接 yt-dlp 下载服务 " + baseUrl + "，请先启动 ai-learning-ytdlp-service", e);
        } catch (SocketTimeoutException e) {
            throw new IOException("yt-dlp 下载服务响应超时，请稍后重试或检查下载服务状态", e);
        } catch (UnknownHostException e) {
            throw new IOException("无法解析 yt-dlp 下载服务地址：" + baseUrl, e);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8091";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @Data
    private static class DownloadRequest {
        private String url;
        private String outputDir;
        private Long resourceId;
    }

    @Data
    public static class DownloadResponse {
        private Boolean success;
        private Long resourceId;
        private String relativePath;
        private String playUrl;
        private String title;
        private Integer duration;
        private String thumbnail;
        private Long fileSize;
    }
}
