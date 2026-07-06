package com.cupk.aiacademy.agent.tool.exam;

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
public class ExamSessionClosePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认关闭考试";

    private final AgentPendingActionStore pendingActionStore;

    public ExamSessionClosePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "exam_session_close_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_close_plan", "exam_end_plan", "exam_session_end_plan", "exam_session_finish_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("examSessionId"));
        parameters.put("optionalFields", List.of("reason"));

        AgentActionPlan plan = new AgentActionPlan(
                "exam.session.close",
                "关闭考试安排",
                "exam",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备关闭考试安排；执行前必须补齐考试安排ID，并输入二次确认文本。",
                parameters,
                List.of("会将考试安排标记为已结束", "学生将不能继续通过该考试入口参加考试", "只有已发布的考试可以关闭")
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
