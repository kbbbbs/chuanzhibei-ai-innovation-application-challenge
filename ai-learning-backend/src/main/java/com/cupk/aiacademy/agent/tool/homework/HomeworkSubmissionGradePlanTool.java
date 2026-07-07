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
public class HomeworkSubmissionGradePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认批改作业";

    private final AgentPendingActionStore pendingActionStore;

    public HomeworkSubmissionGradePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "homework_submission_grade_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("homework_grade_plan", "homework_submit_grade_plan", "assignment_grade_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("homeworkSubmitId", "score"));
        parameters.put("optionalFields", List.of("feedback"));

        AgentActionPlan plan = new AgentActionPlan(
                "homework.submission.grade",
                "批改作业提交",
                "homework",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备批改一条作业提交；执行前必须补齐提交ID和得分，并输入二次确认文本。",
                parameters,
                List.of("会更新学生作业提交得分", "会记录批改教师、批改时间和反馈",
                        "当前动作只更新作业提交记录，不自动同步成绩台账")
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
