package com.cupk.aiacademy.agent.tool.grade;

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
public class GradeRecordDeletePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认删除成绩";

    private final AgentPendingActionStore pendingActionStore;

    public GradeRecordDeletePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "grade_record_delete_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("grade_delete_plan", "score_delete_plan", "grade_remove_plan", "score_remove_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("gradeRecordId"));
        parameters.put("optionalFields", List.of("reason"));

        AgentActionPlan plan = new AgentActionPlan(
                "grade.record.delete",
                "删除成绩记录",
                "grade",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备删除一条成绩记录；执行前必须补齐成绩ID，并输入二次确认文本。",
                parameters,
                List.of("会删除学生成绩台账记录", "会影响学生成绩查询结果", "删除后不能通过 Agent 自动恢复")
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
