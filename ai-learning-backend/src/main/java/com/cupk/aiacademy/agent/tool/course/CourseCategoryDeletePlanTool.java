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
public class CourseCategoryDeletePlanTool implements AgentTool {
    public static final String CONFIRMATION_TEXT = "确认删除课程分类";

    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "course.category.delete",
            "删除课程分类",
            "course",
            AgentActionRiskLevel.HIGH,
            true,
            CONFIRMATION_TEXT,
            "根据用户自然语言请求准备删除课程分类；执行前必须通过分类ID或分类名称唯一定位。分类下有课程时默认拒绝，只有显式 cascade=true 才级联删除课程。",
            List.of(
                    new AgentActionParameterSpec("categoryId", "课程分类ID", false, "优先使用分类 ID 定位",
                            AgentActionParameterType.INTEGER, List.of(),
                            List.of("分类ID", "分类编号", "id"), List.of("3"),
                            null, BigDecimal.ONE, null, ""),
                    new AgentActionParameterSpec("name", "分类名称", false, "没有 categoryId 时用于唯一定位要删除的分类",
                            AgentActionParameterType.STRING, List.of(),
                            List.of("课程分类", "分类名", "分类名称", "名称", "名字"),
                            List.of("计算机基础"), 100, null, null, ""),
                    new AgentActionParameterSpec("cascade", "是否级联删除课程", false, "仅用户明确要求连同分类下课程一起删除时设为 true",
                            AgentActionParameterType.BOOLEAN, List.of(),
                            List.of("级联删除", "连同课程一起删除", "一起删"), List.of("false")),
                    new AgentActionParameterSpec("reason", "删除原因", false)
            ),
            List.of(List.of("categoryId", "name")),
            List.of("会物理删除课程分类记录", "分类下有课程时默认拒绝删除",
                    "cascade=true 会同时删除该分类下课程记录，影响范围更大", "仅管理员可以执行"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public CourseCategoryDeletePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "course_category_delete_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("category_delete_plan", "course_category_remove_plan", "category_remove_plan");
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
