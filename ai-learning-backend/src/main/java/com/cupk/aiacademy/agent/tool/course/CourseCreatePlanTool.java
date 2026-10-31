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
public class CourseCreatePlanTool implements AgentTool {
    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "course.create",
            "创建课程",
            "course",
            AgentActionRiskLevel.MEDIUM,
            true,
            "",
            "根据用户自然语言请求准备创建课程；执行前必须补齐课程名称、分类等关键参数，并由用户确认。",
            List.of(
                    new AgentActionParameterSpec("title", "课程名称", true, "要创建的课程标题",
                            AgentActionParameterType.STRING, List.of(), List.of("课程标题", "课程名", "课名", "名称", "名字"),
                            List.of("Java 入门"), 100, null, null, ""),
                    new AgentActionParameterSpec("categoryId", "课程分类ID", false, "课程所属分类的数字 ID；没有 ID 时可用分类名称",
                            AgentActionParameterType.INTEGER, List.of(), List.of("分类ID", "分类编号", "课程分类ID"),
                            List.of("1"), null, BigDecimal.ONE, null, ""),
                    new AgentActionParameterSpec("categoryName", "课程分类名称", false, "没有 categoryId 时用分类名称唯一定位，例如 人工智能、计算机",
                            AgentActionParameterType.STRING, List.of(), List.of("分类名称", "课程分类", "分类"),
                            List.of("人工智能"), 50, null, null, ""),
                    new AgentActionParameterSpec("description", "课程描述", false, "课程介绍文本",
                            AgentActionParameterType.STRING, List.of(), List.of(), List.of(), 1000, null, null, ""),
                    new AgentActionParameterSpec("cover", "封面标识", false, "封面文件或资源标识",
                            AgentActionParameterType.STRING, List.of(), List.of(), List.of(), 255, null, null, ""),
                    new AgentActionParameterSpec("coverUrl", "封面地址", false, "封面图片 URL",
                            AgentActionParameterType.STRING, List.of(), List.of("封面URL", "封面链接"), List.of(), 255, null, null, ""),
                    new AgentActionParameterSpec("teacher", "教师展示名", false, "课程展示用教师名称",
                            AgentActionParameterType.STRING, List.of(), List.of("教师", "授课老师"), List.of(), 50, null, null, ""),
                    new AgentActionParameterSpec("difficulty", "课程难度", false, "课程难度标签",
                            AgentActionParameterType.STRING, List.of(), List.of("难度"), List.of(), 20, null, null, ""),
                    new AgentActionParameterSpec("teacherId", "教师用户ID", false, "教师默认当前用户；管理员可指定",
                            AgentActionParameterType.INTEGER, List.of(), List.of("教师ID", "老师ID"), List.of(), null, BigDecimal.ONE, null, ""),
                    new AgentActionParameterSpec("studentCount", "学习人数", false, "默认 0",
                            AgentActionParameterType.INTEGER, List.of(), List.of("学生数", "学习人数"), List.of(), null, BigDecimal.ZERO, null, ""),
                    new AgentActionParameterSpec("viewCount", "浏览量", false, "默认 0",
                            AgentActionParameterType.INTEGER, List.of(), List.of("浏览次数"), List.of(), null, BigDecimal.ZERO, null, ""),
                    new AgentActionParameterSpec("rating", "评分", false, "0 到 5",
                            AgentActionParameterType.NUMBER, List.of(), List.of("课程评分"), List.of(), null, BigDecimal.ZERO, BigDecimal.valueOf(5), ""),
                    new AgentActionParameterSpec("status", "课程状态", false, "默认发布状态；0 下架，1 发布，2 草稿",
                            AgentActionParameterType.INTEGER, List.of("0", "1", "2"), List.of("状态", "课程状态"), List.of("1"))
            ),
            List.of(List.of("categoryId", "categoryName")),
            List.of("会新增一条课程记录", "课程可作为章节、课时、教学班、考试和成绩的上游对象",
                    "教师默认使用当前登录教师，管理员可指定 teacherId"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public CourseCreatePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "course_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_publish_plan", "course_add_plan");
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
