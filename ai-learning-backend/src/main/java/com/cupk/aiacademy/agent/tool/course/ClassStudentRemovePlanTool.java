package com.cupk.aiacademy.agent.tool.course;

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
public class ClassStudentRemovePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认移除班级学生";

    private final AgentPendingActionStore pendingActionStore;

    public ClassStudentRemovePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "class_student_remove_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("class_student_delete_plan", "teaching_class_student_remove_plan",
                "class_member_remove_plan", "student_unenroll_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("classStudentId 或 classId+studentId"));
        parameters.put("optionalFields", List.of("reason"));

        AgentActionPlan plan = new AgentActionPlan(
                "class.student.remove",
                "移除班级学生",
                "teaching_class",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备从教学班学生名单中移除学生；执行前必须定位名单记录，并输入二次确认文本。",
                parameters,
                List.of("会删除教学班学生名单记录", "不会删除学生账号", "不会删除已有学习进度或成绩记录")
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
