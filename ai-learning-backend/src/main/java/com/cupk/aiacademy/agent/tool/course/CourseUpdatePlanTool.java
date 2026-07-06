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
public class CourseUpdatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public CourseUpdatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "course_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_modify_plan", "course_edit_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("courseId"));
        parameters.put("optionalFields", List.of("title", "description", "cover", "coverUrl",
                "teacher", "difficulty", "categoryId", "rating"));

        AgentActionPlan plan = new AgentActionPlan(
                "course.update",
                "修改课程",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备修改已有课程；执行前必须补齐课程ID和至少一个可修改字段，并由用户确认。",
                parameters,
                List.of("会修改课程基础展示信息", "不允许通过该动作修改课程教师归属、学习人数、浏览量或状态",
                        "教师只能修改自己负责的课程")
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
