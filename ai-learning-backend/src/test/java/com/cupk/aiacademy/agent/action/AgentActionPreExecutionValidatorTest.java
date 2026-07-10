package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.tool.homework.HomeworkCreatePlanTool;
import com.cupk.aiacademy.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentActionPreExecutionValidatorTest {
    @Test
    void validateStripsPlanMetadataAndNormalizesTypes() {
        AgentActionPreExecutionValidator validator = validator();

        Map<String, Object> parameters = validator.validateAndNormalize("homework.create", Map.of(
                "rawRequest", "发布作业",
                "parameterSchema", List.of(),
                "classId", "3",
                "title", "第一次作业",
                "fullScore", "100"
        ));

        assertEquals(3, parameters.get("classId"));
        assertEquals("第一次作业", parameters.get("title"));
        assertEquals(0, new java.math.BigDecimal("100").compareTo((java.math.BigDecimal) parameters.get("fullScore")));
        assertEquals(false, parameters.containsKey("rawRequest"));
    }

    @Test
    void validateRejectsMissingRequiredAnyOf() {
        AgentActionPreExecutionValidator validator = validator();

        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validateAndNormalize(
                "homework.create",
                Map.of("title", "第一次作业")
        ));

        assertEquals("参数校验失败，缺少：classId 或 className 或 courseId 或 courseTitle", exception.getMessage());
    }

    @Test
    void validateRejectsUnknownExecutableField() {
        AgentActionPreExecutionValidator validator = validator();

        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validateAndNormalize(
                "homework.create",
                Map.of("classId", 3, "title", "第一次作业", "unexpected", "x")
        ));

        assertEquals("参数校验失败，问题：unexpected 字段不在动作 schema 中", exception.getMessage());
    }

    private AgentActionPreExecutionValidator validator() {
        AgentActionSpecRegistry registry = new AgentActionSpecRegistry();
        AgentActionCatalog catalog = new AgentActionCatalog(
                List.of(new HomeworkCreatePlanTool(new InMemoryAgentPendingActionStore())),
                registry
        );
        return new AgentActionPreExecutionValidator(catalog, new AgentActionSchemaValidator(), null);
    }
}
