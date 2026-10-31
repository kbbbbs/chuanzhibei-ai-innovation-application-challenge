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
public class QuestionUpdatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public QuestionUpdatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "question_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_question_update_plan", "question_modify_plan", "question_edit_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("questionId"));
        parameters.put("optionalFields", List.of("courseId", "teacherId", "type", "content", "answers",
                "options", "difficulty", "score", "knowledgePoint", "analysis", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "question.update",
                "修改试题",
                "exam",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备修改题库试题；执行前必须补齐试题ID和至少一个可修改字段。",
                parameters,
                List.of("会修改已有题库记录", "教师只能修改自己负责课程下的试题",
                        "变更题型、选项或答案时会重新执行题型规则校验")
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
