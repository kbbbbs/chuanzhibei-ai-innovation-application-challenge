package com.cupk.aiacademy.agent.orchestrator;

import com.cupk.aiacademy.agent.action.AgentActionCatalog;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentActionSpecRegistry;
import com.cupk.aiacademy.agent.action.InMemoryAgentPendingActionStore;
import com.cupk.aiacademy.agent.core.config.AgentConfigurationParser;
import com.cupk.aiacademy.agent.tool.AgentToolExecutor;
import com.cupk.aiacademy.agent.tool.course.CourseCategoryCreatePlanTool;
import com.cupk.aiacademy.agent.tool.course.CourseCreatePlanTool;
import com.cupk.aiacademy.agent.tool.course.CourseDeletePlanTool;
import com.cupk.aiacademy.agent.tool.course.CourseStatusPlanTool;
import com.cupk.aiacademy.agent.tool.homework.HomeworkCreatePlanTool;
import com.cupk.aiacademy.agent.tool.teacher.TeacherApplicationReviewPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import com.cupk.aiacademy.pojo.AgentDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionIntentRouterTest {
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void routesLegacyPlanToolThroughSchemaPlanFactory() {
        loginAs(7);
        InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
        HomeworkCreatePlanTool tool = new HomeworkCreatePlanTool(pendingActionStore);
        AgentActionSpecRegistry registry = new AgentActionSpecRegistry();
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(tool), registry);
        AgentActionPlanFactory planFactory = new AgentActionPlanFactory(pendingActionStore);
        AgentActionIntentRouter router = new AgentActionIntentRouter(
                new AgentConfigurationParser(null),
                new AgentToolExecutor(List.of(tool)),
                null,
                catalog,
                planFactory
        );
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"homework_create_plan\"]");

        AgentDirectActionReply reply = router.buildDirectActionReply(
                agent,
                "发布作业，教学班ID 1，标题叫第一次作业"
        ).orElseThrow();

        assertEquals("动作待确认", reply.content());
        assertNotNull(reply.pendingAction());
        assertEquals("homework.create", reply.pendingAction().actionCode());
        assertTrue(reply.pendingAction().parameters().containsKey("parameterSchema"));
        assertTrue(reply.pendingAction().parameters().containsKey("missingRequiredFields"));
    }

    @Test
    void routesCourseCategoryCreateBeforeCourseCreate() {
        loginAs(1);
        InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
        AgentActionPlanFactory planFactory = new AgentActionPlanFactory(pendingActionStore);
        CourseCategoryCreatePlanTool categoryTool = new CourseCategoryCreatePlanTool(planFactory);
        CourseCreatePlanTool courseTool = new CourseCreatePlanTool(planFactory);
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(categoryTool, courseTool), new AgentActionSpecRegistry());
        AgentActionIntentRouter router = new AgentActionIntentRouter(
                new AgentConfigurationParser(null),
                new AgentToolExecutor(List.of(categoryTool, courseTool)),
                null,
                catalog,
                planFactory
        );
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"course_create_plan\",\"course_category_create_plan\"]");

        AgentDirectActionReply reply = router.buildDirectActionReply(
                agent,
                "新增一个课程分类，名字叫计算机基础"
        ).orElseThrow();

        assertNotNull(reply.pendingAction());
        assertEquals("course.category.create", reply.pendingAction().actionCode());
    }

    @Test
    void routesCourseCreateWithCategoryParameterToCourseCreate() {
        loginAs(1);
        InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
        AgentActionPlanFactory planFactory = new AgentActionPlanFactory(pendingActionStore);
        CourseCategoryCreatePlanTool categoryTool = new CourseCategoryCreatePlanTool(planFactory);
        CourseCreatePlanTool courseTool = new CourseCreatePlanTool(planFactory);
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(categoryTool, courseTool), new AgentActionSpecRegistry());
        AgentActionIntentRouter router = new AgentActionIntentRouter(
                new AgentConfigurationParser(null),
                new AgentToolExecutor(List.of(categoryTool, courseTool)),
                null,
                catalog,
                planFactory
        );
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"course_create_plan\",\"course_category_create_plan\"]");

        AgentDirectActionReply reply = router.buildDirectActionReply(
                agent,
                "新建一门课程，名字叫 Java 入门，课程分类1"
        ).orElseThrow();

        assertNotNull(reply.pendingAction());
        assertEquals("course.create", reply.pendingAction().actionCode());
    }

    @Test
    void routesCourseDeleteSeparatelyFromCourseStatus() {
        loginAs(1);
        InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
        AgentActionPlanFactory planFactory = new AgentActionPlanFactory(pendingActionStore);
        CourseDeletePlanTool deleteTool = new CourseDeletePlanTool(planFactory);
        CourseStatusPlanTool statusTool = new CourseStatusPlanTool(planFactory);
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(deleteTool, statusTool), new AgentActionSpecRegistry());
        AgentActionIntentRouter router = new AgentActionIntentRouter(
                new AgentConfigurationParser(null),
                new AgentToolExecutor(List.of(deleteTool, statusTool)),
                null,
                catalog,
                planFactory
        );
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"course_delete_plan\",\"course_status_plan\"]");

        AgentDirectActionReply deleteReply = router.buildDirectActionReply(
                agent,
                "删除 Java 入门 这门课"
        ).orElseThrow();
        AgentDirectActionReply statusReply = router.buildDirectActionReply(
                agent,
                "下架 Java 入门 这门课"
        ).orElseThrow();

        assertEquals("course.delete", deleteReply.pendingAction().actionCode());
        assertEquals("course.status", statusReply.pendingAction().actionCode());
    }

    @Test
    void routesTeacherApplicationReviewRequest() {
        loginAs(1);
        InMemoryAgentPendingActionStore pendingActionStore = new InMemoryAgentPendingActionStore();
        AgentActionPlanFactory planFactory = new AgentActionPlanFactory(pendingActionStore);
        TeacherApplicationReviewPlanTool reviewTool = new TeacherApplicationReviewPlanTool(planFactory);
        AgentActionCatalog catalog = new AgentActionCatalog(List.of(reviewTool), new AgentActionSpecRegistry());
        AgentActionIntentRouter router = new AgentActionIntentRouter(
                new AgentConfigurationParser(null),
                new AgentToolExecutor(List.of(reviewTool)),
                null,
                catalog,
                planFactory
        );
        AgentDefinition agent = new AgentDefinition();
        agent.setEnabledTools("[\"teacher_application_review_plan\"]");

        AgentDirectActionReply reply = router.buildDirectActionReply(
                agent,
                "审核通过教师申请ID 61，意见是资料完整"
        ).orElseThrow();

        assertNotNull(reply.pendingAction());
        assertEquals("teacher.application.review", reply.pendingAction().actionCode());
    }

    private void loginAs(Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setRoleCode("teacher");
        UserContext.set(user);
    }
}
