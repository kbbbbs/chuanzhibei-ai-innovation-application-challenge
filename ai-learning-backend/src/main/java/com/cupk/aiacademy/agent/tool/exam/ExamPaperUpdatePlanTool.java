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
public class ExamPaperUpdatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public ExamPaperUpdatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "exam_paper_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_paper_modify_plan", "paper_update_plan", "paper_modify_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("paperId"));
        parameters.put("optionalFields", List.of("courseId", "teacherId", "title", "description",
                "totalScore", "durationMinutes", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "exam.paper.update",
                "修改试卷",
                "exam",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备修改考试试卷基础信息；执行前必须补齐试卷ID和至少一个可修改字段。",
                parameters,
                List.of("会修改已有试卷基础信息", "不会自动调整试卷题目关系或考试安排",
                        "教师只能修改自己负责课程下的试卷")
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
