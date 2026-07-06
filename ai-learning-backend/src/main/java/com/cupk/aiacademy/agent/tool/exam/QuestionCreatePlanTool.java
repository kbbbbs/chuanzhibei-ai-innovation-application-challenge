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
public class QuestionCreatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public QuestionCreatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "question_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_question_create_plan", "question_add_plan", "question_bank_create_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("courseId", "type", "content", "answers"));
        parameters.put("optionalFields", List.of("teacherId", "options", "difficulty", "score",
                "knowledgePoint", "analysis", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "question.create",
                "创建试题",
                "exam",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备创建题库试题；执行前必须补齐课程、题型、题干和标准答案，选择题还必须补齐选项。",
                parameters,
                List.of("会新增一条题库记录", "教师只能给自己负责的课程创建试题",
                        "选择题、判断题、填空题和简答题会按题型规则校验选项和答案")
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
