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
public class GradeRecordCreatePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认录入成绩";

    private final AgentPendingActionStore pendingActionStore;

    public GradeRecordCreatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "grade_record_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("grade_create_plan", "score_create_plan", "grade_input_plan", "score_input_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("studentId", "classId", "courseId", "gradeName", "score"));
        parameters.put("optionalFields", List.of("teacherId", "sourceType", "sourceId", "fullScore", "comment"));

        AgentActionPlan plan = new AgentActionPlan(
                "grade.record.create",
                "录入成绩记录",
                "grade",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备录入一条成绩记录；执行前必须补齐学生、教学班、课程、成绩名称和分数，并输入二次确认文本。",
                parameters,
                List.of("会新增学生成绩台账", "会影响学生成绩查询结果", "默认成绩来源为 manual，默认满分为 100")
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
