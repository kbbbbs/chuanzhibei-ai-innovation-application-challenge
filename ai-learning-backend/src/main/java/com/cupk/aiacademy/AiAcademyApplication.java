package com.cupk.aiacademy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiAcademyApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAcademyApplication.class, args);
    }
}
