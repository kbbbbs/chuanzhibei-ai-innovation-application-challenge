package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.dto.RecommendationResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recommendation-report-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:contest-demo-schema.sql",
        "spring.sql.init.data-locations=classpath:contest-demo-seed.sql"
})
@Transactional
class RecommendationServiceIntegrationTest {
    @Autowired
    private RecommendationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loginAsStudent() {
        AuthUtils.LoginUser student = new AuthUtils.LoginUser();
        student.setUserId(3);
        student.setRoleCode("student");
        UserContext.set(student);
        jdbc.update("INSERT INTO t_paper (arxiv_id, title, `year`, citation_count) VALUES (?, ?, ?, ?)",
                "regression-year", "Recommendation regression", 2026, 10);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "course", "lesson", "exercise", "paper"})
    void studentRecommendationsLoadWithTheDemoSchema(String type) {
        RecommendationResponseDTO response = service.recommendForCurrentUser(type, 24);

        assertNotNull(response.getItems());
        assertNotNull(response.getCounts());
        assertNotNull(response.getSummary());
        if ("all".equals(type) || "paper".equals(type)) {
            assertTrue(response.getItems().stream().anyMatch(item ->
                    "paper".equals(item.getType()) && Integer.valueOf(2026).equals(item.getMeta().get("year"))));
        }
    }
}
