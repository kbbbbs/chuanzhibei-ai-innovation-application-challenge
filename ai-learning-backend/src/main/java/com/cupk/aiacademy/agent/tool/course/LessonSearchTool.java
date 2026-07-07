package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Lesson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class LessonSearchTool implements AgentTool {
    private final LessonMapper lessonMapper;
    private final AgentToolSupport support;

    public LessonSearchTool(LessonMapper lessonMapper, AgentToolSupport support) {
        this.lessonMapper = lessonMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "lesson_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("lesson_query", "course_lesson_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<Lesson> page = new Page<>(1, context.maxItems());
        QueryWrapper<Lesson> query = new QueryWrapper<>();
        if (!support.applyLessonVisibility(query)) {
            return AgentToolResult.read("没有可查询的课时范围。");
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "resource_type", "content");
        query.orderByAsc("chapter_id").orderByAsc("sort_order").orderByAsc("id");
        lessonMapper.selectPage(page, query);
        List<Lesson> lessons = page.getRecords();
        if (lessons == null || lessons.isEmpty()) {
            return AgentToolResult.read("未查询到匹配课时。");
        }

        List<String> lines = new ArrayList<>();
        for (Lesson lesson : lessons) {
            lines.add("课时ID=" + support.value(lesson.getId())
                    + "；章节ID=" + support.value(lesson.getChapterId())
                    + "；标题=" + support.value(lesson.getTitle())
                    + "；资源类型=" + support.value(lesson.getResourceType())
                    + "；时长=" + support.value(lesson.getDuration())
                    + "；状态=" + support.value(lesson.getStatus())
                    + "；内容=" + support.summarize(lesson.getContent(), 100));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
