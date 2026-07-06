package com.cupk.aiacademy.agent.tool.notice;

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
public class NoticeCreatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public NoticeCreatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "notice_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("notice_publish_plan", "announcement_create_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("publisherId", "title", "content"));
        parameters.put("optionalFields", List.of("classId", "noticeType", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "notice.create",
                "发布通知",
                "notice",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备发布通知；执行前必须补齐标题、正文、发布人和目标范围，并由用户确认。",
                parameters,
                List.of("会新增一条通知记录", "可能影响目标教学班或全站用户的信息展示")
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
