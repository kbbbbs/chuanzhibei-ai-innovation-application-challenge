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
public class NoticeUpdatePlanTool implements AgentTool {
    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "notice.update",
            "修改通知",
            "notice",
            AgentActionRiskLevel.MEDIUM,
            true,
            "",
            "根据用户自然语言请求准备修改已有通知；执行前必须提供要更新的字段，并优先使用通知ID定位；没有通知ID时可用原通知标题唯一定位。",
            List.of(
                    new AgentActionParameterSpec("noticeId", "通知ID", false, "优先使用通知ID定位通知",
                            AgentActionParameterType.INTEGER, List.of(), List.of("通知编号", "id"), List.of("201")),
                    new AgentActionParameterSpec("originalTitle", "原通知标题", false, "没有 noticeId 时用于唯一定位原通知",
                            AgentActionParameterType.STRING, List.of(), List.of("原通知标题", "原标题", "原通知名", "旧标题"), List.of("考试安排")),
                    new AgentActionParameterSpec("title", "新的通知标题", false, "修改后的通知标题",
                            AgentActionParameterType.STRING, List.of(), List.of("新标题", "新名称", "标题", "主题", "名称"), List.of("期末考试安排")),
                    new AgentActionParameterSpec("content", "通知正文", false, "修改后的通知正文",
                            AgentActionParameterType.STRING, List.of(), List.of("正文", "内容"), List.of("明天上午十点期末考试")),
                    new AgentActionParameterSpec("noticeType", "通知类型", false, "修改后的通知类型"),
                    new AgentActionParameterSpec("classId", "教学班ID", false, "修改后目标教学班；为空表示全站或默认范围",
                            AgentActionParameterType.INTEGER, List.of(), List.of("班级ID", "教学班编号"), List.of("1")),
                    new AgentActionParameterSpec("className", "教学班名称", false, "没有 classId 时用于定位教学班；为空表示全站或默认范围",
                            AgentActionParameterType.STRING, List.of(), List.of("班级名", "班级名称"), List.of("Java 入门 1 班"))
            ),
            List.of(List.of("noticeId", "originalTitle"), List.of("title", "content", "noticeType", "classId", "className")),
            List.of("会修改通知标题、正文、类型或目标范围",
                    "不允许通过该动作修改通知发布人、发布时间或状态",
                    "教师只能修改自己发布的通知"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public NoticeUpdatePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "notice_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("notice_modify_plan", "notice_edit_plan", "announcement_update_plan");
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
