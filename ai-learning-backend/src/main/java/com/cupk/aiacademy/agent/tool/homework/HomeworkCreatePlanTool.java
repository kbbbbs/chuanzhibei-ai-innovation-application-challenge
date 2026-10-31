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
public class HomeworkCreatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public HomeworkCreatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "homework_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("homework_publish_plan", "assignment_create_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("classId", "teacherId", "title"));
        parameters.put("optionalFields", List.of("content", "attachmentUrl", "fullScore", "startTime", "deadline", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "homework.create",
                "发布作业",
                "homework",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备发布作业；执行前必须补齐教学班、教师、标题等关键参数，并由用户确认。",
                parameters,
                List.of("会新增一条作业记录", "会影响目标教学班学生的作业列表和提交入口")
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
