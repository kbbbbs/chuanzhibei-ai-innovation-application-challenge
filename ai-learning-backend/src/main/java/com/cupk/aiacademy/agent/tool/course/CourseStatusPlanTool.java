package com.cupk.aiacademy.agent.tool.course;

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
public class CourseStatusPlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认变更课程状态";

    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "course.status",
            "变更课程状态",
            "course",
            AgentActionRiskLevel.HIGH,
            true,
            CONFIRMATION_TEXT,
            "根据用户自然语言请求准备上下架、停用或恢复课程；执行前必须确认目标状态，并优先提供课程ID；没有课程ID时可用标题、教师、难度和分类ID唯一定位。",
            List.of(
                    new AgentActionParameterSpec("courseId", "课程ID", false, "优先使用课程ID定位课程",
                            AgentActionParameterType.INTEGER, List.of(), List.of("课程编号", "id"), List.of("101")),
                    new AgentActionParameterSpec("status", "目标状态", true, "只能是 0、1 或 2；0 表示下架/停用，1 表示上架/启用，2 表示草稿",
                            AgentActionParameterType.INTEGER, List.of("0", "1", "2"), List.of("状态", "课程状态"), List.of("0")),
                    new AgentActionParameterSpec("title", "课程名称", false, "没有 courseId 时用于唯一定位",
                            AgentActionParameterType.STRING, List.of(), List.of("课程标题", "课程名", "课名", "名称", "名字"), List.of("Java 入门")),
                    new AgentActionParameterSpec("teacher", "教师展示名", false, "没有 courseId 时用于唯一定位"),
                    new AgentActionParameterSpec("difficulty", "课程难度", false, "没有 courseId 时用于唯一定位"),
                    new AgentActionParameterSpec("categoryId", "课程分类ID", false, "没有 courseId 时用于唯一定位",
                            AgentActionParameterType.INTEGER, List.of(), List.of("分类ID", "分类编号", "课程分类ID"), List.of("1")),
                    new AgentActionParameterSpec("categoryName", "课程分类名称", false, "没有 categoryId 时用于唯一定位",
                            AgentActionParameterType.STRING, List.of(), List.of("分类名称", "课程分类"), List.of("计算机")),
                    new AgentActionParameterSpec("reason", "变更原因", false)
            ),
            List.of(List.of("courseId", "title")),
            List.of("会改变课程对用户的可见或可用状态", "可能影响章节、课时、教学班和学习路径入口",
                    "该动作优先做状态变更，不物理删除课程记录"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public CourseStatusPlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "course_status_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_publish_status_plan", "course_archive_plan",
                "course_delete_plan", "course_remove_plan");
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
