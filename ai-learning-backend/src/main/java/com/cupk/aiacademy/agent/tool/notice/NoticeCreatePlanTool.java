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
public class NoticeCreatePlanTool implements AgentTool {
    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "notice.create",
            "发布通知",
            "notice",
            AgentActionRiskLevel.MEDIUM,
            true,
            "",
            "根据用户自然语言请求准备发布通知；执行前必须补齐标题、正文、发布人和目标范围，并由用户确认。",
            List.of(
                    new AgentActionParameterSpec("publisherId", "发布人ID", false, "默认使用当前登录用户",
                            AgentActionParameterType.INTEGER, List.of(), List.of("发布人", "发布者ID"), List.of("1")),
                    new AgentActionParameterSpec("publisherName", "发布人名称", false, "没有 publisherId 时用于定位发布人",
                            AgentActionParameterType.STRING, List.of(), List.of("发布人姓名", "发布者"), List.of("张老师")),
                    new AgentActionParameterSpec("title", "通知标题", true, "通知标题",
                            AgentActionParameterType.STRING, List.of(), List.of("标题", "主题"), List.of("考试安排")),
                    new AgentActionParameterSpec("content", "通知正文", true, "通知正文内容",
                            AgentActionParameterType.STRING, List.of(), List.of("正文", "内容"), List.of("明天上午十点考试")),
                    new AgentActionParameterSpec("classId", "教学班ID", false, "为空时表示全站或默认范围",
                            AgentActionParameterType.INTEGER, List.of(), List.of("班级ID", "教学班编号"), List.of("1")),
                    new AgentActionParameterSpec("className", "教学班名称", false, "没有 classId 时用于定位教学班；为空时表示全站或默认范围",
                            AgentActionParameterType.STRING, List.of(), List.of("班级名", "班级名称"), List.of("Java 入门 1 班")),
                    new AgentActionParameterSpec("noticeType", "通知类型", false),
                    new AgentActionParameterSpec("status", "通知状态", false, "默认发布状态")
            ),
            List.of("会新增一条通知记录", "可能影响目标教学班或全站用户的信息展示"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public NoticeCreatePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
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
    public Optional<AgentActionSpec> actionSpec() {
        return Optional.of(SPEC);
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        return planFactory.createWritePlan(name(), SPEC, context);
    }
}
