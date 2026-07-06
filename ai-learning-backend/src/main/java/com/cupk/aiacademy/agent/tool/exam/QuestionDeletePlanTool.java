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
public class QuestionDeletePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认删除试题";

    private final AgentPendingActionStore pendingActionStore;

    public QuestionDeletePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "question_delete_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_question_delete_plan", "question_remove_plan", "question_bank_delete_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("questionId"));
        parameters.put("optionalFields", List.of("reason"));

        AgentActionPlan plan = new AgentActionPlan(
                "question.delete",
                "删除试题",
                "exam",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备删除题库试题；执行前必须补齐试题ID，并输入二次确认文本。",
                parameters,
                List.of("会物理删除题库试题记录",
                        "数据库外键会级联删除该试题在试卷中的题目关联和考试答题记录",
                        "教师只能删除自己负责课程下的试题",
                        "删除后不能通过 Agent 自动恢复")
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
