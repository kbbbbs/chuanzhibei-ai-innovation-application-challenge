package com.cupk.aiacademy.agent.planner;

import com.cupk.aiacademy.agent.action.AgentActionParameterSpec;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReadToolCatalogTest {
    @Test
    void exposesToolSpecificStructuredSchemas() {
        AgentReadToolCatalog catalog = new AgentReadToolCatalog(List.of(
                tool("course_category_search"),
                tool("homework_submission_stats"),
                tool("course_structure_search")
        ));

        List<AgentReadToolSpec> specs = catalog.enabledSpecs(List.of(
                "course_category_search", "homework_submission_stats", "course_structure_search"));

        AgentReadToolSpec category = spec(specs, "course_category_search");
        assertEquals(List.of("name", "listAll", "maxItems"), names(category));
        assertFalse(category.getParameters().stream().anyMatch(parameter -> "query".equals(parameter.name())));

        AgentReadToolSpec homework = spec(specs, "homework_submission_stats");
        assertEquals(List.of(List.of("homeworkTitle", "className")), homework.getRequiredAnyOf());

        AgentReadToolSpec structure = spec(specs, "course_structure_search");
        assertTrue(structure.getParameters().stream()
                .anyMatch(parameter -> "courseTitle".equals(parameter.name()) && parameter.required()));
    }

    private AgentReadToolSpec spec(List<AgentReadToolSpec> specs, String name) {
        return specs.stream().filter(spec -> name.equals(spec.getToolName())).findFirst().orElseThrow();
    }

    private List<String> names(AgentReadToolSpec spec) {
        return spec.getParameters().stream().map(AgentActionParameterSpec::name).toList();
    }

    private AgentTool tool(String name) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public AgentToolResult execute(AgentToolContext context) {
                return AgentToolResult.read("");
            }
        };
    }
}
