package com.cupk.aiacademy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {
    /**
     * The browser reaches the backend through the same-origin /backend proxy (nginx, Vite),
     * so no cross-origin access is granted unless exact origins are configured.
     */
    @Bean
    public CorsFilter corsFilter(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.addAllowedHeader("*");
            config.addAllowedMethod("*");
            config.setAllowCredentials(false);
            source.registerCorsConfiguration("/**", config);
        }
        return new CorsFilter(source);
    }
}
