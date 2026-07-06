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
public class ExamPaperQuestionRemovePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认移除试卷题目";

    private final AgentPendingActionStore pendingActionStore;

    public ExamPaperQuestionRemovePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "exam_paper_question_remove_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("paper_question_remove_plan", "exam_paper_question_delete_plan", "paper_question_delete_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("paperId", "paperQuestionId 或 questionId"));
        parameters.put("optionalFields", List.of("reason"));

        AgentActionPlan plan = new AgentActionPlan(
                "exam.paper.question.remove",
                "试卷移除题目",
                "exam",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备从试卷移除题目；执行前必须补齐试卷ID和试卷题目关系ID或题目ID，并输入二次确认文本。",
                parameters,
                List.of("只允许从草稿试卷移除题目",
                        "会删除试卷题目关系并重算试卷总分，但不会删除题库题目本身",
                        "如果该试卷题目已被答卷使用，则拒绝移除")
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
