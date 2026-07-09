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
public class CourseUpdatePlanTool implements AgentTool {
    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "course.update",
            "修改课程",
            "course",
            AgentActionRiskLevel.MEDIUM,
            true,
            "",
            "根据用户自然语言请求准备修改已有课程；执行前必须提供要更新的字段，并优先使用课程ID定位；没有课程ID时可用原课程名称、教师、难度和分类ID唯一定位。",
            List.of(
                    new AgentActionParameterSpec("courseId", "课程ID", false, "优先使用课程ID定位课程",
                            AgentActionParameterType.INTEGER, List.of(), List.of("课程编号", "id"), List.of("101")),
                    new AgentActionParameterSpec("originalTitle", "原课程名称", false, "没有 courseId 时用于唯一定位原课程",
                            AgentActionParameterType.STRING, List.of(), List.of("原课程名", "原课名", "原标题", "原名称", "原有标题", "旧标题"), List.of("我不想吃肉")),
                    new AgentActionParameterSpec("title", "新的课程名称", false, "修改后的课程标题",
                            AgentActionParameterType.STRING, List.of(), List.of("新标题", "新名称", "新课程名", "新课名", "课程名称", "课程标题", "课程名", "课名", "名称", "名字"), List.of("我想吃肉")),
                    new AgentActionParameterSpec("description", "课程描述", false),
                    new AgentActionParameterSpec("cover", "封面标识", false),
                    new AgentActionParameterSpec("coverUrl", "封面地址", false),
                    new AgentActionParameterSpec("teacher", "教师展示名", false, "没有 courseId 时用于唯一定位"),
                    new AgentActionParameterSpec("difficulty", "课程难度", false, "没有 courseId 时用于唯一定位"),
                    new AgentActionParameterSpec("categoryId", "课程分类ID", false, "新的课程分类数字 ID",
                            AgentActionParameterType.INTEGER, List.of(), List.of("分类ID", "分类编号", "课程分类ID"), List.of("1")),
                    new AgentActionParameterSpec("categoryName", "课程分类名称", false, "没有 categoryId 时用分类名称唯一定位，例如 人工智能、计算机",
                            AgentActionParameterType.STRING, List.of(), List.of("分类名称", "课程分类", "分类"), List.of("人工智能")),
                    new AgentActionParameterSpec("rating", "评分", false, "0 到 5")
            ),
            List.of(List.of("courseId", "originalTitle")),
            List.of("会修改课程基础展示信息", "不允许通过该动作修改课程教师归属、学习人数、浏览量或状态",
                    "教师只能修改自己负责的课程"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public CourseUpdatePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "course_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_modify_plan", "course_edit_plan");
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
