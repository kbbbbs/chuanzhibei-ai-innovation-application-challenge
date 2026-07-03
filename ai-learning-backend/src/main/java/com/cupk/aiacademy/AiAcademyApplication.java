package com.cupk.aiacademy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.cupk.aiacademy.repository")
public class AiAcademyApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAcademyApplication.class, args);
    }
}
