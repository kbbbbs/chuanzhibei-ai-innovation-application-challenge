package com.weknora.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WeKnora 配置类
 * 在 application.yml 中添加：
 *   weknora:
 *     base-url: http://localhost:8081
 *     api-key: sk-your-api-key
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "weknora")
public class WeKnoraConfig {
    /** WeKnora 服务地址，默认 http://localhost:8081 */
    private String baseUrl = "http://localhost:8081";
    /** WeKnora API Key，在 WeKnora 前端界面生成（格式 sk-xxx） */
    private String apiKey;
}
