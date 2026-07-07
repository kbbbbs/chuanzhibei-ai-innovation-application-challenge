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
public class ExamPaperQuestionUpdatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public ExamPaperQuestionUpdatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "exam_paper_question_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("paper_question_update_plan", "exam_paper_question_modify_plan", "paper_question_modify_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("paperId", "paperQuestionId 或 questionId"));
        parameters.put("optionalFields", List.of("score", "sortOrder", "requiredAnswer"));

        AgentActionPlan plan = new AgentActionPlan(
                "exam.paper.question.update",
                "调整试卷题目",
                "exam",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备调整试卷题目分值、顺序或必答设置；执行前必须补齐试卷ID、试卷题目关系ID或题目ID，以及至少一个可修改字段。",
                parameters,
                List.of("只允许调整草稿试卷中的题目关系",
                        "会更新试卷题目关系并重算试卷总分",
                        "如果该试卷题目已被答卷使用，则拒绝调整")
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
