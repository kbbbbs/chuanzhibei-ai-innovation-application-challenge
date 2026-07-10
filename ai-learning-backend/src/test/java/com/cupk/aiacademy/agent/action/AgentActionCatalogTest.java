package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.tool.course.ChapterCreatePlanTool;
import com.cupk.aiacademy.agent.tool.course.CourseCreatePlanTool;
import com.cupk.aiacademy.agent.tool.course.CourseStatusPlanTool;
import com.cupk.aiacademy.agent.tool.notice.NoticeCreatePlanTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionCatalogTest {
    @Test
    void catalogCollectsSpecsFromPlanToolsAndAliases() {
        AgentActionPlanFactory planFactory = new AgentActionPlanFactory(new InMemoryAgentPendingActionStore());
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(
                new ChapterCreatePlanTool(planFactory),
                new CourseCreatePlanTool(planFactory),
                new CourseStatusPlanTool(planFactory),
                new NoticeCreatePlanTool(planFactory)
        ));

        assertEquals(4, catalog.allSpecs().size());
        assertEquals("chapter.create", catalog.findByToolName("chapter_add_plan").orElseThrow().actionCode());
        assertEquals("course.create", catalog.findByToolName("course_add_plan").orElseThrow().actionCode());
        assertEquals("course.status", catalog.findByActionCode("course.status").orElseThrow().actionCode());
        assertEquals(List.of("course.create", "notice.create"),
                catalog.enabledSpecs(List.of("course_create_plan", "notice_publish_plan"))
                        .stream()
                        .map(AgentActionSpec::actionCode)
                        .toList());
        assertTrue(catalog.enabledActionSummaries(List.of("course_create_plan")).getFirst()
                .containsKey("requiredFields"));
    }
}
