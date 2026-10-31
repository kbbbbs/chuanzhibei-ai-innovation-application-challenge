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
public class CourseDeletePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认删除课程";

    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "course.delete",
            "删除课程",
            "course",
            AgentActionRiskLevel.HIGH,
            true,
            CONFIRMATION_TEXT,
            "根据用户自然语言请求准备删除课程；执行前必须唯一定位课程。删除课程会调用课程 DELETE 接口等价的物理删除动作，不是下架或状态变更。",
            List.of(
                    new AgentActionParameterSpec("courseId", "课程ID", false, "优先使用课程ID定位课程",
                            AgentActionParameterType.INTEGER, List.of(), List.of("课程编号", "id"), List.of("101")),
                    new AgentActionParameterSpec("title", "课程名称", false, "没有 courseId 时用于唯一定位",
                            AgentActionParameterType.STRING, List.of(), List.of("课程标题", "课程名", "课名", "名称", "名字"), List.of("Java 入门")),
                    new AgentActionParameterSpec("teacher", "教师展示名", false, "没有 courseId 时用于唯一定位"),
                    new AgentActionParameterSpec("difficulty", "课程难度", false, "没有 courseId 时用于唯一定位"),
                    new AgentActionParameterSpec("categoryId", "课程分类ID", false, "没有 courseId 时用于唯一定位",
                            AgentActionParameterType.INTEGER, List.of(), List.of("分类ID", "分类编号", "课程分类ID"), List.of("1")),
                    new AgentActionParameterSpec("categoryName", "课程分类名称", false, "没有 categoryId 时用于唯一定位",
                            AgentActionParameterType.STRING, List.of(), List.of("分类名称", "课程分类"), List.of("计算机")),
                    new AgentActionParameterSpec("reason", "删除原因", false)
            ),
            List.of(List.of("courseId", "title")),
            List.of("会物理删除课程记录", "不会只修改课程状态", "教师只能删除自己负责的课程"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public CourseDeletePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "course_delete_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_remove_plan");
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
