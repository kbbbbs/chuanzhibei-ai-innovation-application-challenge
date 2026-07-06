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
public class GradeRecordUpdatePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认修改成绩";

    private final AgentPendingActionStore pendingActionStore;

    public GradeRecordUpdatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "grade_record_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("grade_update_plan", "score_update_plan", "score_modify_plan", "grade_modify_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("gradeRecordId", "score"));
        parameters.put("optionalFields", List.of("fullScore", "comment", "gradeName"));

        AgentActionPlan plan = new AgentActionPlan(
                "grade.record.update",
                "修改成绩记录",
                "grade",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备修改已有成绩记录；执行前必须补齐成绩ID和新分数，并输入二次确认文本。",
                parameters,
                List.of("会修改学生成绩台账", "会影响学生成绩查询结果", "不允许修改学生、课程、教学班和成绩来源归属")
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
