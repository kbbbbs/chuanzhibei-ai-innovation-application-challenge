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
public class CourseResourceRemovePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认移除课程资源";

    private final AgentPendingActionStore pendingActionStore;

    public CourseResourceRemovePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "course_resource_remove_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("lesson_resource_remove_plan", "course_resource_delete_plan",
                "lesson_resource_delete_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("lessonId"));
        parameters.put("optionalFields", List.of("removeFields", "reason"));
        parameters.put("supportedRemoveFields", List.of("resourceType", "resourceUrl", "videoUrl", "content"));

        AgentActionPlan plan = new AgentActionPlan(
                "course.resource.remove",
                "移除课程资源",
                "course",
                AgentActionRiskLevel.HIGH,
                true,
                CONFIRMATION_TEXT,
                "根据用户自然语言请求准备移除课时上的视频、文档、资料链接或正文资源；执行前必须补齐课时ID，并输入二次确认文本。",
                parameters,
                List.of("会清空指定课时上的一个或多个资源字段", "可能影响学生查看课程资料",
                        "不删除课时记录，也不删除文件存储中的原始文件")
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
