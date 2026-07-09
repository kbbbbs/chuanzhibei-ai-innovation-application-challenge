package com.cupk.aiacademy.agent.action;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionSchemaValidatorTest {
    private final AgentActionSchemaValidator validator = new AgentActionSchemaValidator();

    @Test
    void validateNormalizesAliasesAndTypes() {
        AgentActionParameterValidationResult result = validator.validate(spec(), Map.of(
                "课程标题", "Java 入门",
                "分类ID", "1",
                "状态", "0"
        ));

        assertEquals("Java 入门", result.parameters().get("title"));
        assertEquals(1, result.parameters().get("categoryId"));
        assertEquals(0, result.parameters().get("status"));
        assertTrue(result.missingRequiredFields().isEmpty());
        assertTrue(result.issues().isEmpty());
        assertTrue(result.valid());
    }

    @Test
    void validateReportsEnumAndUnknownFieldIssues() {
        AgentActionParameterValidationResult result = validator.validate(spec(), Map.of(
                "课程标题", "Java 入门",
                "分类ID", "1",
                "状态", "9",
                "extra", "ignored"
        ));

        assertEquals(List.of("status"), result.missingRequiredFields());
        assertFalse(result.valid());
        List<String> issueCodes = result.issues().stream()
                .map(AgentActionValidationIssue::code)
                .toList();
        assertEquals(2, issueCodes.size());
        assertTrue(issueCodes.contains("enum_mismatch"));
        assertTrue(issueCodes.contains("unknown_field"));
    }

    @Test
    void validateReportsRequiredAnyOfAndFieldConstraints() {
        AgentActionParameterValidationResult missing = validator.validate(courseCreateAnyOfSpec(), Map.of(
                "title", "Java 入门"
        ));

        assertEquals(List.of("categoryId 或 categoryName"), missing.missingRequiredFields());
        assertFalse(missing.valid());

        AgentActionParameterValidationResult valid = validator.validate(courseCreateAnyOfSpec(), Map.of(
                "title", "Java 入门",
                "categoryName", "计算机"
        ));

        assertTrue(valid.missingRequiredFields().isEmpty());
        assertTrue(valid.issues().isEmpty());

        AgentActionParameterValidationResult tooLong = validator.validate(courseCreateAnyOfSpec(), Map.of(
                "title", "这是一门标题过长的课程",
                "categoryId", "1"
        ));

        assertEquals(List.of("title"), tooLong.missingRequiredFields());
        assertEquals(List.of("max_length_exceeded"),
                tooLong.issues().stream().map(AgentActionValidationIssue::code).toList());
    }

    private AgentActionSpec spec() {
        return new AgentActionSpec(
                "course.create",
                "创建课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备创建课程",
                List.of(
                        new AgentActionParameterSpec("title", "课程名称", true, "",
                                AgentActionParameterType.STRING, List.of(), List.of("课程标题"), List.of()),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", true, "",
                                AgentActionParameterType.INTEGER, List.of(), List.of("分类ID"), List.of()),
                        new AgentActionParameterSpec("status", "课程状态", true, "",
                                AgentActionParameterType.INTEGER, List.of("0", "1", "2"), List.of("状态"), List.of())
                ),
                List.of(),
                Duration.ofMinutes(10)
        );
    }

    private AgentActionSpec courseCreateAnyOfSpec() {
        return new AgentActionSpec(
                "course.create",
                "创建课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备创建课程",
                List.of(
                        new AgentActionParameterSpec("title", "课程名称", true, "",
                                AgentActionParameterType.STRING, List.of(), List.of(), List.of(),
                                8, null, null, ""),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", false, "",
                                AgentActionParameterType.INTEGER, List.of(), List.of(), List.of(),
                                null, BigDecimal.ONE, null, ""),
                        new AgentActionParameterSpec("categoryName", "课程分类名称", false)
                ),
                List.of(List.of("categoryId", "categoryName")),
                List.of(),
                Duration.ofMinutes(10)
        );
    }
}
