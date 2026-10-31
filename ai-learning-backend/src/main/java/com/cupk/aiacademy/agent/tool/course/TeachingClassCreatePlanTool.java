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
public class TeachingClassCreatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public TeachingClassCreatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "teaching_class_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("class_create_plan", "teaching_class_add_plan", "class_add_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("courseId", "className"));
        parameters.put("optionalFields", List.of("teacherId", "semester", "description", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "teaching.class.create",
                "创建教学班",
                "teaching_class",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备创建教学班；执行前必须补齐课程ID、教学班名称等关键参数，并由用户确认。",
                parameters,
                List.of("会新增一条教学班记录", "教学班会作为作业、考试、通知和学生名单的作用范围",
                        "教师默认只能创建自己负责课程下的教学班")
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
