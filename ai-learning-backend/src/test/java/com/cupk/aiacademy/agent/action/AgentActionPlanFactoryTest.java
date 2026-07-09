package com.cupk.aiacademy.agent.action;

import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolOperationType;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionPlanFactoryTest {
    private final InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
    private final AgentActionPlanFactory planFactory = new AgentActionPlanFactory(pendingActionStore);

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createWritePlanReturnsStructuredPendingAction() {
        loginAs(7);

        AgentToolResult result = planFactory.createWritePlan(
                "notice_create_plan",
                spec(),
                new AgentToolContext("发布一条明天交作业的通知", 1)
        );

        assertEquals("notice_create_plan", result.toolName());
        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertEquals("notice.create", result.data().get("actionCode"));
        assertTrue(result.data().containsKey("actionId"));
        assertTrue(result.data().containsKey("expiresAt"));
        assertEquals(List.of("title", "content"), result.data().get("missingRequiredFields"));
        assertTrue(result.content().contains("待补齐字段=title(通知标题)、content(通知正文)"));

        AgentPendingAction pendingAction = pendingActionStore.findById(String.valueOf(result.data().get("actionId")))
                .orElse(null);
        assertNotNull(pendingAction);
        assertEquals("notice.create", pendingAction.actionCode());
        assertEquals(7, pendingAction.createdBy());
        assertEquals("发布一条明天交作业的通知", pendingAction.parameters().get("rawRequest"));
    }

    @Test
    void createWritePlanWithoutLoginDoesNotCreatePendingAction() {
        AgentToolResult result = planFactory.createWritePlan(
                "notice_create_plan",
                spec(),
                new AgentToolContext("发布通知", 1)
        );

        assertEquals(AgentToolOperationType.WRITE_PLAN, result.operationType());
        assertTrue(!result.data().containsKey("actionId"));
        assertTrue(!result.content().contains("确认ID="));
    }

    @Test
    void createWritePlanInfersCourseCreateParametersFromNaturalLanguage() {
        loginAs(7);

        AgentToolResult result = planFactory.createWritePlan(
                "course_create_plan",
                courseCreateSpec(),
                new AgentToolContext("帮我创建一门课程，标题是我不想吃肉，分类ID是1", 1)
        );

        String actionId = String.valueOf(result.data().get("actionId"));
        AgentPendingAction pendingAction = pendingActionStore.findById(actionId).orElseThrow();
        Map<?, ?> resultParameters = (Map<?, ?>) result.data().get("parameters");

        assertEquals("我不想吃肉", resultParameters.get("title"));
        assertEquals(1, resultParameters.get("categoryId"));
        assertEquals(List.of(), result.data().get("missingRequiredFields"));
        assertTrue(result.content().contains("已识别参数=title(课程名称)=我不想吃肉、categoryId(课程分类ID)=1"));
        assertEquals("我不想吃肉", pendingAction.parameters().get("title"));
        assertEquals(1, pendingAction.parameters().get("categoryId"));
        assertEquals(List.of(), pendingAction.parameters().get("missingRequiredFields"));
    }

    @Test
    void createWritePlanIncludesRequiredAnyOfMissingFields() {
        loginAs(7);

        AgentToolResult result = planFactory.createWritePlan(
                "course_create_plan",
                courseCreateAnyOfSpec(),
                new AgentToolContext("新增一门课，名字叫 Java 入门", 1)
        );

        String actionId = String.valueOf(result.data().get("actionId"));
        AgentPendingAction pendingAction = pendingActionStore.findById(actionId).orElseThrow();

        assertEquals(List.of("categoryId 或 categoryName"), result.data().get("missingRequiredFields"));
        assertTrue(result.content().contains("待补齐字段=categoryId 或 categoryName"));
        assertEquals(List.of("categoryId 或 categoryName"), pendingAction.parameters().get("missingRequiredFields"));
    }

    private AgentActionSpec spec() {
        return new AgentActionSpec(
                "notice.create",
                "发布通知",
                "notice",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备发布通知",
                List.of(
                        new AgentActionParameterSpec("title", "通知标题", true),
                        new AgentActionParameterSpec("content", "通知正文", true),
                        new AgentActionParameterSpec("classId", "教学班ID", false)
                ),
                List.of("会新增通知"),
                Duration.ofMinutes(10)
        );
    }

    private AgentActionSpec courseCreateSpec() {
        return new AgentActionSpec(
                "course.create",
                "创建课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "",
                "准备创建课程",
                List.of(
                        new AgentActionParameterSpec("title", "课程名称", true),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", true)
                ),
                List.of("会新增课程"),
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
                        new AgentActionParameterSpec("title", "课程名称", true),
                        new AgentActionParameterSpec("categoryId", "课程分类ID", false),
                        new AgentActionParameterSpec("categoryName", "课程分类名称", false)
                ),
                List.of(List.of("categoryId", "categoryName")),
                List.of("会新增课程"),
                Duration.ofMinutes(10)
        );
    }

    private void loginAs(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setRoleCode("admin");
        UserContext.set(user);
    }
}
