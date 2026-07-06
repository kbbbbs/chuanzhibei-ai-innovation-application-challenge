package com.cupk.aiacademy.agent.tool.homework;

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
public class HomeworkUpdatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public HomeworkUpdatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "homework_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("homework_modify_plan", "assignment_update_plan", "assignment_modify_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("homeworkId"));
        parameters.put("optionalFields", List.of("title", "content", "attachmentUrl", "fullScore",
                "startTime", "deadline", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "homework.update",
                "修改作业",
                "homework",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备修改已有作业；执行前必须补齐作业ID和至少一个可修改字段，并由用户确认。",
                parameters,
                List.of("会修改作业展示内容或开放时间", "会影响目标教学班学生看到的作业信息",
                        "不允许通过 Agent 修改作业教师归属")
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
