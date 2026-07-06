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
public class TeachingClassUpdatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public TeachingClassUpdatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "teaching_class_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("class_update_plan", "teaching_class_modify_plan",
                "class_modify_plan", "teaching_class_edit_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("classId"));
        parameters.put("optionalFields", List.of("courseId", "teacherId", "className",
                "semester", "description"));

        AgentActionPlan plan = new AgentActionPlan(
                "teaching.class.update",
                "修改教学班",
                "teaching_class",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备修改教学班；执行前必须补齐教学班ID和至少一个可修改字段，并由用户确认。",
                parameters,
                List.of("会修改教学班基础信息或归属课程", "教师只能修改自己负责的教学班",
                        "如需结束或恢复教学班，优先使用状态变更动作")
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
