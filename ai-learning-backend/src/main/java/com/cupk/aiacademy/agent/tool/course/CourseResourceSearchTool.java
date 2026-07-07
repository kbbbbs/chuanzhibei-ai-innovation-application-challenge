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
public class CourseResourceSearchTool implements AgentTool {
    private final LessonMapper lessonMapper;
    private final AgentToolSupport support;

    public CourseResourceSearchTool(LessonMapper lessonMapper, AgentToolSupport support) {
        this.lessonMapper = lessonMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "course_resource_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("resource_search", "lesson_resource_search");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<Lesson> page = new Page<>(1, context.maxItems());
        QueryWrapper<Lesson> query = new QueryWrapper<>();
        if (!support.applyLessonVisibility(query)) {
            return AgentToolResult.read("没有可查询的课程资源范围。");
        }
        query.and(wrapper -> wrapper.isNotNull("resource_url")
                .or().isNotNull("video_url")
                .or().isNotNull("content"));
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "resource_type", "content");
        query.orderByAsc("chapter_id").orderByAsc("sort_order").orderByAsc("id");
        lessonMapper.selectPage(page, query);
        List<Lesson> lessons = page.getRecords();
        if (lessons == null || lessons.isEmpty()) {
            return AgentToolResult.read("未查询到匹配课程资源。");
        }

        List<String> lines = new ArrayList<>();
        for (Lesson lesson : lessons) {
            lines.add("资源课时ID=" + support.value(lesson.getId())
                    + "；章节ID=" + support.value(lesson.getChapterId())
                    + "；标题=" + support.value(lesson.getTitle())
                    + "；类型=" + support.value(lesson.getResourceType())
                    + "；资源地址=" + support.value(lesson.getResourceUrl())
                    + "；视频地址=" + support.value(lesson.getVideoUrl())
                    + "；内容=" + support.summarize(lesson.getContent(), 100));
        }
        return AgentToolResult.read(String.join("\n", lines));
    }
}
