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
public class ExamPaperQuestionAddPlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public ExamPaperQuestionAddPlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "exam_paper_question_add_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("paper_question_add_plan", "exam_paper_question_attach_plan", "paper_question_attach_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("paperId", "questionId"));
        parameters.put("optionalFields", List.of("score", "sortOrder", "requiredAnswer"));

        AgentActionPlan plan = new AgentActionPlan(
                "exam.paper.question.add",
                "试卷添加题目",
                "exam",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备把题库题目加入试卷；执行前必须补齐试卷ID和题目ID。",
                parameters,
                List.of("只允许给草稿试卷添加题目",
                        "题目必须属于试卷同一课程和同一教师",
                        "不会自动发布试卷或创建考试安排")
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
