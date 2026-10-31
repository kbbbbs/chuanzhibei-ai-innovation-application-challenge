package com.cupk.aiacademy.agent.tool.teacher;

import com.cupk.aiacademy.agent.action.AgentActionParameterSpec;
import com.cupk.aiacademy.agent.action.AgentActionParameterType;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class TeacherApplicationReviewPlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认审核教师申请";

    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "teacher.application.review",
            "审核教师申请",
            "teacher",
            AgentActionRiskLevel.HIGH,
            true,
            CONFIRMATION_TEXT,
            "根据用户自然语言请求准备审核教师入驻申请；执行前必须能定位申请，并明确通过或拒绝。",
            List.of(
                    new AgentActionParameterSpec("applicationId", "教师申请ID", false, "优先使用申请 ID 定位",
                            AgentActionParameterType.INTEGER, List.of(),
                            List.of("申请ID", "教师申请编号", "id"), List.of("61"),
                            null, BigDecimal.ONE, null, ""),
                    new AgentActionParameterSpec("username", "申请用户名", false, "没有 applicationId 时用申请用户名唯一定位",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("申请人", "用户名", "账号"), List.of("zhangsan"),
                            100, null, null, ""),
                    new AgentActionParameterSpec("status", "审核结果", true, "1 表示通过，2 表示拒绝",
                            AgentActionParameterType.INTEGER, List.of("1", "2"),
                            List.of("结果", "审核状态", "通过", "拒绝"), List.of("1")),
                    new AgentActionParameterSpec("reviewComment", "审核意见", false, "审核备注",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("意见", "备注", "原因"), List.of("资料完整，同意入驻"),
                            255, null, null, "")
            ),
            List.of(List.of("applicationId", "username")),
            List.of("会改变教师申请审核状态",
                    "通过申请会创建教师用户并绑定 teacher 角色",
                    "拒绝申请不会创建用户",
                    "仅管理员可以执行"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public TeacherApplicationReviewPlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "teacher_application_review_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("teacher_apply_review_plan", "teacher_application_approve_plan",
                "teacher_application_reject_plan");
    }

    @Override
    public Optional<AgentActionSpec> actionSpec() {
        return Optional.of(SPEC);
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        return planFactory.createWritePlan(name(), SPEC, context);
    }
}
