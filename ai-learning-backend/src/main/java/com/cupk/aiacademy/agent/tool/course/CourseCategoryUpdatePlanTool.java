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
public class CourseCategoryUpdatePlanTool implements AgentTool {
    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "course.category.update",
            "修改课程分类",
            "course",
            AgentActionRiskLevel.MEDIUM,
            true,
            "",
            "根据用户自然语言请求准备修改课程分类；执行前必须能通过分类ID或原分类名称唯一定位，并提供至少一个要修改的字段。",
            List.of(
                    new AgentActionParameterSpec("categoryId", "课程分类ID", false, "优先使用分类 ID 定位",
                            AgentActionParameterType.INTEGER, List.of(),
                            List.of("分类ID", "分类编号", "id"), List.of("3"),
                            null, BigDecimal.ONE, null, ""),
                    new AgentActionParameterSpec("categoryName", "原分类名称", false, "没有 categoryId 时用于唯一定位原分类",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("原分类名", "原分类名称", "旧分类名", "当前分类名", "要修改的分类", "课程分类"),
                            List.of("计算机"), 100, null, null, ""),
                    new AgentActionParameterSpec("name", "新的分类名称", false, "修改后的课程分类名称",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("新分类名", "新分类名称", "新名称", "新名字", "改名为", "改成", "改为"),
                            List.of("计算机基础"), 100, null, null, ""),
                    new AgentActionParameterSpec("description", "分类描述", false, "新的分类说明；传空表示清空",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("描述", "说明", "简介"), List.of("计算机基础方向课程"),
                            255, null, null, ""),
                    new AgentActionParameterSpec("sortOrder", "排序值", false, "正整数；小于等于 0 时清空排序值",
                            AgentActionParameterType.INTEGER, List.of(),
                            List.of("排序", "排序号", "sort_order"), List.of("1"),
                            null, null, null, "")
            ),
            List.of(List.of("categoryId", "categoryName"), List.of("name", "description", "sortOrder")),
            List.of("会修改课程分类展示信息", "不会自动修改分类下课程本身", "仅管理员可以执行"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public CourseCategoryUpdatePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "course_category_update_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("category_update_plan", "course_category_modify_plan", "category_modify_plan");
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
