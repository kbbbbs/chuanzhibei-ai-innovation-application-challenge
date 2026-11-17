package com.cupk.aiacademy.analysis;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;


@Component
public class LearningAnalysisSchema {
    private final JdbcTemplate db;
    public LearningAnalysisSchema(JdbcTemplate db) { this.db = db; }

    @PostConstruct public void initialize() throws Exception {
        try (var connection = db.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V009__learning_analysis_reports.sql"));
        }
    }
}
