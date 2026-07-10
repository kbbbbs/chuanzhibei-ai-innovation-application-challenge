package com.cupk.aiacademy.agent.tool.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.tool.api.AgentToolContext;
import com.cupk.aiacademy.agent.tool.api.AgentToolResult;
import com.cupk.aiacademy.agent.tool.support.AgentToolSupport;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseCategoryReadToolTest {
    @Test
    void categorySearchListsAllCategoriesWhenPlannerUsesWildcard() {
        CourseCategoryMapper categoryMapper = mock(CourseCategoryMapper.class);
        when(categoryMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(category(1, "计算机"), category(2, "人工智能")));

        AgentToolResult result = new CourseCategorySearchTool(categoryMapper)
                .execute(new AgentToolContext("", 50, Map.of("listAll", true, "maxItems", 50)));

        assertTrue(result.content().contains("名称=计算机"));
        assertTrue(result.content().contains("名称=人工智能"));
    }

    @Test
    void courseByCategorySearchResolvesCategoryThenQueriesVisibleCourses() {
        CourseCategoryMapper categoryMapper = mock(CourseCategoryMapper.class);
        CourseMapper courseMapper = mock(CourseMapper.class);
        AgentToolSupport support = mock(AgentToolSupport.class);
        when(categoryMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(category(1, "计算机组成原理")));
        when(support.applyCourseVisibility(any(QueryWrapper.class))).thenReturn(true);
        Course course = new Course();
        course.setId(8);
        course.setTitle("计算机组成原理基础");
        course.setTeacher("王老师");
        course.setStatus(1);
        when(courseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(course));
        when(support.value(any())).thenAnswer(invocation -> String.valueOf((Object) invocation.getArgument(0)));

        AgentToolResult result = new CourseByCategorySearchTool(categoryMapper, courseMapper, support)
                .execute(new AgentToolContext("", 10,
                        Map.of("categoryName", "计算机组成原理", "maxItems", 10)));

        assertTrue(result.content().contains("名称=计算机组成原理基础"));
        assertTrue(result.content().contains("教师=王老师"));
    }

    private CourseCategory category(int id, String name) {
        CourseCategory category = new CourseCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
