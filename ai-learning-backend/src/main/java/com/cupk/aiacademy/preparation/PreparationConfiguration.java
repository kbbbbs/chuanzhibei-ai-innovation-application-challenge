package com.cupk.aiacademy.preparation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class PreparationConfiguration {
    @Bean(name = "preparationTaskExecutor")
    public Executor preparationTaskExecutor(@Value("${preparation.max-concurrency:2}") int concurrency) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int size = Math.max(1, Math.min(4, concurrency));
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("preparation-task-");
        executor.initialize();
        return executor;
    }

}
