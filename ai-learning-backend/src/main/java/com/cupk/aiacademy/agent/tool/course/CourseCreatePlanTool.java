package com.cupk.aiacademy.agent.tool.course;

import com.cupk.aiacademy.agent.action.AgentActionPlan;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.action.AgentPendingAction;
import com.cupk.aiacademy.agent.action.AgentPendingActionStore;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.common.UserContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CourseCreatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public CourseCreatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "course_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_publish_plan", "course_add_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("title", "categoryId"));
        parameters.put("optionalFields", List.of("description", "cover", "coverUrl", "teacher",
                "difficulty", "teacherId", "studentCount", "viewCount", "rating", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "course.create",
                "创建课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备创建课程；执行前必须补齐课程名称、分类等关键参数，并由用户确认。",
                parameters,
                List.of("会新增一条课程记录", "课程可作为章节、课时、教学班、考试和成绩的上游对象",
                        "教师默认使用当前登录教师，管理员可指定 teacherId")
        );

        Map<String, Object> data = new LinkedHashMap<>(plan.toData());
        String content = plan.toPromptContent();
        Integer currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null) {
            AgentPendingAction pendingAction = pendingActionStore.create(
                    plan.actionCode(),
                    currentUserId,
                    Map.of("rawRequest", context.normalizedQuery()),
                    Duration.ofMinutes(10)
            );
            data.put("actionId", pendingAction.actionId());
            data.put("expiresAt", pendingAction.expiresAt().toString());
            content = content + "；确认ID=" + pendingAction.actionId() + "；过期时间=" + pendingAction.expiresAt();
        }
        return AgentToolResult.writePlan(name(), content, data);
    }
}
