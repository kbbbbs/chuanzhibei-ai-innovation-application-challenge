package com.cupk.aiacademy.agent.tool.notice;

import com.cupk.aiacademy.agent.action.AgentActionParameterSpec;
import com.cupk.aiacademy.agent.action.AgentActionParameterType;
import com.cupk.aiacademy.agent.action.AgentActionPlanFactory;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.action.AgentActionSpec;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class NoticeStatusPlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认变更通知状态";

    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "notice.status",
            "变更通知状态",
            "notice",
            AgentActionRiskLevel.HIGH,
            true,
            CONFIRMATION_TEXT,
            "根据用户自然语言请求准备撤回、恢复或下线通知；执行前必须确认目标状态，并优先提供通知ID；没有通知ID时可用通知标题唯一定位。",
            List.of(
                    new AgentActionParameterSpec("noticeId", "通知ID", false, "优先使用通知ID定位通知",
                            AgentActionParameterType.INTEGER, List.of(), List.of("通知编号", "id"), List.of("201")),
                    new AgentActionParameterSpec("status", "目标状态", true, "只能是 0 或 1；0 表示撤回/下线，1 表示发布/启用",
                            AgentActionParameterType.INTEGER, List.of("0", "1"), List.of("状态", "通知状态"), List.of("0")),
                    new AgentActionParameterSpec("title", "通知标题", false, "没有 noticeId 时用于唯一定位",
                            AgentActionParameterType.STRING, List.of(), List.of("通知标题", "标题", "主题", "名称"), List.of("考试安排")),
                    new AgentActionParameterSpec("classId", "教学班ID", false, "按通知标题定位时可用于消歧",
                            AgentActionParameterType.INTEGER, List.of(), List.of("班级ID", "教学班编号"), List.of("1")),
                    new AgentActionParameterSpec("className", "教学班名称", false, "按通知标题定位时可用于消歧",
                            AgentActionParameterType.STRING, List.of(), List.of("班级名", "班级名称"), List.of("Java 入门 1 班")),
                    new AgentActionParameterSpec("reason", "变更原因", false)
            ),
            List.of(List.of("noticeId", "title")),
            List.of("会改变通知对用户的可见状态",
                    "撤回后目标教学班或全站用户将不再看到该通知",
                    "该动作优先做状态变更，不物理删除通知记录"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public NoticeStatusPlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "notice_status_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("notice_archive_plan", "notice_withdraw_plan", "notice_delete_plan", "notice_remove_plan");
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
