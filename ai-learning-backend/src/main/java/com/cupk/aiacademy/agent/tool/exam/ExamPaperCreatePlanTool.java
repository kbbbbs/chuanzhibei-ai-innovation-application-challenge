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
public class ExamPaperCreatePlanTool implements AgentTool {
    private final AgentPendingActionStore pendingActionStore;

    public ExamPaperCreatePlanTool(AgentPendingActionStore pendingActionStore) {
        this.pendingActionStore = pendingActionStore;
    }

    @Override
    public String name() {
        return "exam_paper_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("exam_paper_add_plan", "paper_create_plan", "paper_add_plan");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("rawRequest", context.normalizedQuery());
        parameters.put("requiredFields", List.of("courseId", "title"));
        parameters.put("optionalFields", List.of("teacherId", "description", "totalScore",
                "durationMinutes", "status"));

        AgentActionPlan plan = new AgentActionPlan(
                "exam.paper.create",
                "创建试卷",
                "exam",
                AgentActionRiskLevel.MEDIUM,
                true,
                "根据用户自然语言请求准备创建考试试卷；执行前必须补齐课程ID和试卷名称。",
                parameters,
                List.of("会新增一张试卷基础信息记录", "不会自动添加题目或创建考试安排",
                        "教师只能给自己负责的课程创建试卷")
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
