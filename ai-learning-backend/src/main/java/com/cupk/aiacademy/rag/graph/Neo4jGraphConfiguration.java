package com.cupk.aiacademy.rag.graph;

import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class Neo4jGraphConfiguration {
    @Bean(destroyMethod = "close")
    Driver graphDriver(
            @Value("${spring.neo4j.uri:bolt://127.0.0.1:7687}") String uri,
            @Value("${spring.neo4j.authentication.username:neo4j}") String username,
            @Value("${spring.neo4j.authentication.password:}") String password) {
        return GraphDatabase.driver(
                uri,
                AuthTokens.basic(username, password),
                Config.builder()
                        .withConnectionTimeout(5, TimeUnit.SECONDS)
                        .withConnectionAcquisitionTimeout(5, TimeUnit.SECONDS)
                        .withMaxTransactionRetryTime(5, TimeUnit.SECONDS)
                        .build());
    }
}
