package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentTool;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.pojo.CourseCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class CourseCategorySearchTool implements AgentTool {
    private final CourseCategoryMapper categoryMapper;

    public CourseCategorySearchTool(CourseCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public String name() {
        return "course_category_search";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("category_search", "course_category_query");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String categoryName = context.stringArgument("name");
        boolean listAll = Boolean.TRUE.equals(context.booleanArgument("listAll"));
        QueryWrapper<CourseCategory> query = new QueryWrapper<>();
        if (!listAll && !categoryName.isBlank()) {
            query.like("name", categoryName);
        }
        query.orderByAsc("sort_order").orderByAsc("id")
                .last("LIMIT " + Math.max(1, Math.min(50, context.maxItems())));
        List<CourseCategory> categories = categoryMapper.selectList(query);
        if (categories == null || categories.isEmpty()) {
            return AgentToolResult.read(name(), "未查询到匹配的课程分类。", java.util.Map.of());
        }
        String content = categories.stream()
                .map(category -> "分类ID=" + category.getId()
                        + "；名称=" + category.getName()
                        + "；说明=" + value(category.getDescription()))
                .collect(java.util.stream.Collectors.joining("\n"));
        return AgentToolResult.read(name(), content, java.util.Map.of("count", categories.size()));
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "无" : value.trim();
    }
}
