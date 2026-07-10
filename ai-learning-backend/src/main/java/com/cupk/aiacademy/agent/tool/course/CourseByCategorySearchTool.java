package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CourseByCategorySearchTool implements AgentTool {
    private final CourseCategoryMapper categoryMapper;
    private final CourseMapper courseMapper;
    private final AgentToolSupport support;

    public CourseByCategorySearchTool(CourseCategoryMapper categoryMapper,
                                      CourseMapper courseMapper,
                                      AgentToolSupport support) {
        this.categoryMapper = categoryMapper;
        this.courseMapper = courseMapper;
        this.support = support;
    }

    @Override
    public String name() {
        return "course_by_category_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("category_course_search", "courses_in_category");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String categoryName = context.stringArgument("categoryName");
        if (categoryName.isBlank()) {
            return AgentToolResult.failure(name(), declaredOperationType(), "请提供要查询的课程分类名称。");
        }

        List<CourseCategory> categories = findCategories(categoryName);
        if (categories.isEmpty()) {
            return AgentToolResult.read(name(), "未找到课程分类：" + categoryName, java.util.Map.of());
        }
        if (categories.size() > 1) {
            String names = categories.stream().map(CourseCategory::getName).collect(Collectors.joining("、"));
            return AgentToolResult.read(name(), "匹配到多个课程分类，请补充更准确的名称：" + names, java.util.Map.of());
        }

        CourseCategory category = categories.getFirst();
        QueryWrapper<Course> courseQuery = new QueryWrapper<>();
        if (!support.applyCourseVisibility(courseQuery)) {
            return AgentToolResult.read(name(), "当前用户没有可查询的课程范围。", java.util.Map.of());
        }
        courseQuery.eq("category_id", category.getId())
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, Math.min(50, context.maxItems())));
        List<Course> courses = courseMapper.selectList(courseQuery);
        if (courses == null || courses.isEmpty()) {
            return AgentToolResult.read(name(), "分类“" + category.getName() + "”下暂无可见课程。",
                    java.util.Map.of("categoryId", category.getId(), "categoryName", category.getName()));
        }
        String content = courses.stream()
                .map(course -> "课程ID=" + support.value(course.getId())
                        + "；名称=" + support.value(course.getTitle())
                        + "；教师=" + support.value(course.getTeacher())
                        + "；状态=" + support.value(course.getStatus()))
                .collect(Collectors.joining("\n"));
        return AgentToolResult.read(name(), content,
                java.util.Map.of("categoryId", category.getId(), "categoryName", category.getName(),
                        "count", courses.size()));
    }

    private List<CourseCategory> findCategories(String categoryName) {
        QueryWrapper<CourseCategory> exact = new QueryWrapper<>();
        exact.eq("name", categoryName).orderByAsc("id").last("LIMIT 2");
        List<CourseCategory> categories = categoryMapper.selectList(exact);
        if (categories != null && !categories.isEmpty()) {
            return categories;
        }
        QueryWrapper<CourseCategory> fuzzy = new QueryWrapper<>();
        fuzzy.like("name", categoryName).orderByAsc("id").last("LIMIT 6");
        categories = categoryMapper.selectList(fuzzy);
        return categories == null ? List.of() : categories;
    }
}
