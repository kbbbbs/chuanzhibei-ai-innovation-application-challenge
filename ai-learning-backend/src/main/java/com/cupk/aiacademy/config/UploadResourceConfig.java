package com.cupk.aiacademy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Value("${app.media.dir:media}")
    private String mediaDir;

    @Value("${app.media.url-prefix:/media}")
    private String mediaUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        addHandler(registry, uploadDir, urlPrefix, "/uploads");
        addMediaHandler(registry);
    }

    private void addHandler(ResourceHandlerRegistry registry, String dir, String urlPrefix, String defaultPrefix) {
        Path root = Paths.get(dir).toAbsolutePath().normalize();
        String prefix = normalizeUrlPrefix(urlPrefix, defaultPrefix);
        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations(toResourceLocation(root));
    }

    private void addMediaHandler(ResourceHandlerRegistry registry) {
        Path root = Paths.get(mediaDir).toAbsolutePath().normalize();
        String prefix = normalizeUrlPrefix(mediaUrlPrefix, "/media");
        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations(mediaResourceLocations(root));
    }

    private String[] mediaResourceLocations(Path root) {
        Set<String> locations = new LinkedHashSet<>();
        locations.add(toResourceLocation(root));

        Path workingDir = Paths.get("").toAbsolutePath().normalize();
        locations.add(toResourceLocation(workingDir.resolve("ai-learning-ytdlp-service").resolve("media")));
        Path parent = workingDir.getParent();
        if (parent != null) {
            locations.add(toResourceLocation(parent.resolve("ai-learning-ytdlp-service").resolve("media")));
        }
        return locations.toArray(String[]::new);
    }

    private String toResourceLocation(Path path) {
        String location = path.normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }

    private String normalizeUrlPrefix(String value, String defaultPrefix) {
        if (value == null || value.trim().isEmpty()) {
            return defaultPrefix;
        }
        String prefix = value.trim();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        while (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
