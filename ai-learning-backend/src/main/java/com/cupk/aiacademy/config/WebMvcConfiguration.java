package com.cupk.aiacademy.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired(required = false)
    private UsageLogInterceptor usageLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/uploads/**", "/media/**");

        if (usageLogInterceptor != null) {
            registry.addInterceptor(usageLogInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns(
                            "/operation-logs/**",
                            "/api/setup/**",
                            "/api/admin/settings/**",
                            "/uploads/**",
                            "/media/**",
                            "/api/weknora/local-file/**",
                            "/favicon.ico",
                            "/assets/**",
                            "/static/**",
                            "/webjars/**");
        }
    }
}
