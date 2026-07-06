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
public class CourseResourceAttachPlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public CourseResourceAttachPlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "course_resource_attach_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("lesson_resource_attach_plan", "course_resource_bind_plan",
                "lesson_resource_bind_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("lessonId"));
        parameters.put("resourceFields", List.of("resourceType", "resourceUrl", "videoUrl", "content"));
        parameters.put("optionalFields", List.of("reason"));

        AgentActionPlan plan = new AgentActionPlan(
                "course.resource.attach",
                "挂载课程资源",
                "course",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备给课时挂载视频、文档、资料链接或正文内容；执行前必须补齐课时ID和至少一个资源字段，并由用户确认。",
                parameters,
                List.of("会更新指定课时的资源字段", "可能改变学生在课程资源中看到的视频、文档或资料链接",
                        "教师只能给自己负责课程下的课时挂载资源")
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
