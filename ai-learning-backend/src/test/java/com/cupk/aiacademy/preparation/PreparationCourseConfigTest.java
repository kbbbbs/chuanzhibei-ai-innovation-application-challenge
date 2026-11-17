package com.cupk.aiacademy.preparation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreparationCourseConfigTest {
    @Test void courseBindingAndExplicitFallbackSurviveDraftPersistence() throws Exception {
        var json = new ObjectMapper();
        var config = json.readValue("{\"title\":\"算法\",\"targetCourseId\":42,\"useCourseInfoOnly\":true}", PreparationModels.Config.class);
        var stored = json.readTree(json.writeValueAsString(config));
        assertEquals(42, stored.path("targetCourseId").asInt());
        assertTrue(stored.path("useCourseInfoOnly").asBoolean());
    }
}
