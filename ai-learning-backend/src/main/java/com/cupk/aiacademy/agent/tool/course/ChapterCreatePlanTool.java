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
public class ChapterCreatePlanTool implements AgentTool {
    private static final AgentActionSpec SPEC = new AgentActionSpec(
            "chapter.create",
            "创建章节",
            "course",
            AgentActionRiskLevel.MEDIUM,
            true,
            "",
            "根据用户自然语言请求准备创建课程章节；执行前必须补齐章节标题，并通过课程ID或课程名称等条件唯一定位课程。",
            List.of(
                    new AgentActionParameterSpec("courseId", "课程ID", false, "优先使用课程ID定位课程",
                            AgentActionParameterType.INTEGER, List.of(), List.of("课程编号", "id"), List.of("1")),
                    new AgentActionParameterSpec("courseTitle", "课程名称", false, "没有 courseId 时用于唯一定位课程",
                            AgentActionParameterType.STRING, List.of(), List.of("课程标题", "课程名", "课名", "课程名称", "课程名字"), List.of("Java 入门")),
                    new AgentActionParameterSpec("teacher", "教师展示名", false, "没有 courseId 时可作为课程消歧条件"),
                    new AgentActionParameterSpec("difficulty", "课程难度", false, "没有 courseId 时可作为课程消歧条件"),
                    new AgentActionParameterSpec("categoryId", "课程分类ID", false, "没有 courseId 时可作为课程消歧条件",
                            AgentActionParameterType.INTEGER, List.of(), List.of("分类ID", "分类编号", "课程分类ID"), List.of("1")),
                    new AgentActionParameterSpec("categoryName", "课程分类名称", false, "没有 categoryId 时可作为课程消歧条件",
                            AgentActionParameterType.STRING, List.of(), List.of("分类名称", "课程分类"), List.of("计算机")),
                    new AgentActionParameterSpec("title", "章节标题", true, "要创建的章节标题",
                            AgentActionParameterType.STRING, List.of(), List.of("章节名", "章节名称", "章标题", "标题", "名称", "名字"), List.of("第一章 绪论")),
                    new AgentActionParameterSpec("description", "章节描述", false),
                    new AgentActionParameterSpec("sortOrder", "排序值", false, "章节在课程内的排序值",
                            AgentActionParameterType.INTEGER, List.of(), List.of("排序", "顺序", "排序号"), List.of("1"))
            ),
            List.of(List.of("courseId", "courseTitle")),
            List.of("会新增一条章节记录", "章节会进入指定课程的内容结构",
                    "教师只能在自己负责的课程下创建章节", "课程名匹配到多门课程时会要求补充 courseId"),
            Duration.ofMinutes(10)
    );

    private final AgentActionPlanFactory planFactory;

    public ChapterCreatePlanTool(AgentActionPlanFactory planFactory) {
        this.planFactory = planFactory;
    }

    @Override
    public String name() {
        return "chapter_create_plan";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_chapter_create_plan", "chapter_add_plan");
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
