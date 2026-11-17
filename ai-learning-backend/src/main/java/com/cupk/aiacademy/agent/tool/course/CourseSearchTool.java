package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Component
public class CourseSearchTool implements AgentTool {
    private final CourseMapper courseMapper;
    private final AgentToolSupport support;

    public CourseSearchTool(CourseMapper courseMapper, AgentToolSupport support) {
        this.courseMapper = courseMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "course_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("course_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Page<Course> page = new Page<>(1, context.maxItems());
        QueryWrapper<Course> query = new QueryWrapper<>();
        if (!support.applyCourseVisibility(query)) {
            return AgentToolResult.read(name(),"没有可查询的课程范围。",Map.of("count",0,"items",List.of()));
        }
        support.applyKeywordFilter(query, support.extractSearchTerms(context.normalizedQuery()),
                "title", "description", "teacher", "difficulty");
        query.orderByDesc("id");
        courseMapper.selectPage(page, query);
        List<Course> courses = page.getRecords();
        if (courses == null || courses.isEmpty()) {
            return AgentToolResult.read(name(),"未查询到匹配课程。",Map.of("count",0,"items",List.of()));
        }

        List<String> lines = new ArrayList<>();
        for (Course course : courses) {
            lines.add("课程ID=" + support.value(course.getId())
                    + "；名称=" + support.value(course.getTitle())
                    + "；教师=" + support.value(course.getTeacher())
                    + "；难度=" + support.value(course.getDifficulty())
                    + "；简介=" + support.summarize(course.getDescription(), 120));
        }
        var items=courses.stream().map(course->Map.<String,Object>of("id",course.getId(),
                "title",course.getTitle()==null?"":course.getTitle())).toList();
        return AgentToolResult.read(name(),String.join("\n", lines),Map.of("count",items.size(),"items",items));
    }
}
