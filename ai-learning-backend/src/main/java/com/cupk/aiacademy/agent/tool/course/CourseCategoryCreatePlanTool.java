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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class CourseCategoryCreatePlanTool implements AgentTool {
    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "course.category.create",
            "创建课程分类",
            "course",
            AgentActionRiskLevel.MEDIUM,
            true,
            "",
            "根据用户自然语言请求准备创建课程分类；执行前必须补齐分类名称，并由用户确认。",
            List.of(
                    new AgentActionParameterSpec("name", "分类名称", true, "要创建的课程分类名称",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("课程分类", "分类名", "分类名称", "名称", "名字"),
                            List.of("计算机基础"), 100, null, null, ""),
                    new AgentActionParameterSpec("description", "分类描述", false, "课程分类说明",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("描述", "说明", "简介"), List.of("计算机基础方向课程"),
                            255, null, null, ""),
                    new AgentActionParameterSpec("sortOrder", "排序值", false, "正整数；未提供或小于等于 0 时使用系统默认排序",
                            AgentActionParameterType.INTEGER, List.of(),
                            List.of("排序", "排序号", "sort_order"), List.of("1"),
                            null, BigDecimal.ONE, null, "")
            ),
            List.of("会新增一条课程分类记录", "后续创建课程时可用该分类名称或 ID 定位分类"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public CourseCategoryCreatePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "course_category_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("category_create_plan", "course_category_add_plan", "category_add_plan");
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
