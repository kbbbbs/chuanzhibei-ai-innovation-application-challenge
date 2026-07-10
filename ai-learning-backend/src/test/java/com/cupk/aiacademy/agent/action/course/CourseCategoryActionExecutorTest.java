package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.CourseCategoryDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCategoryActionExecutorTest {
    @Mock
    private CourseCategoryMapper categoryMapper;

    @Mock
    private CourseMapper courseMapper;

    @Test
    void createInsertsCategoryForAdmin() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.insert(any(CourseCategory.class))).thenAnswer(invocation -> {
            CourseCategory category = invocation.getArgument(0);
            category.setId(3);
            return 1;
        });
        CourseCategoryCreateActionExecutor executor = new CourseCategoryCreateActionExecutor(categoryMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("admin", 1),
                Map.of("name", "计算机基础", "description", "基础方向", "sortOrder", "2"),
                ""
        ));

        assertTrue(result.success());
        assertEquals("course.category.create", result.actionCode());
        assertEquals(3, result.data().get("categoryId"));

        ArgumentCaptor<CourseCategory> captor = ArgumentCaptor.forClass(CourseCategory.class);
        verify(categoryMapper).insert(captor.capture());
        CourseCategory category = captor.getValue();
        assertEquals("计算机基础", category.getName());
        assertEquals("基础方向", category.getDescription());
        assertEquals(2, category.getSortOrder());
    }

    @Test
    void updateResolvesCategoryByName() {
        CourseCategory category = category(3, "计算机");
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.updateById(any(CourseCategory.class))).thenReturn(1);
        CourseCategoryUpdateActionExecutor executor = new CourseCategoryUpdateActionExecutor(categoryMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("admin", 1),
                Map.of("categoryName", "计算机", "name", "计算机基础", "sortOrder", 0),
                ""
        ));

        assertTrue(result.success());
        assertEquals("计算机", result.data().get("oldName"));
        assertEquals("计算机基础", result.data().get("newName"));

        ArgumentCaptor<CourseCategory> captor = ArgumentCaptor.forClass(CourseCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        CourseCategory updated = captor.getValue();
        assertEquals(3, updated.getId());
        assertEquals("计算机基础", updated.getName());
        assertNull(updated.getSortOrder());
    }

    @Test
    void deleteRejectsWhenCoursesExistWithoutCascade() {
        when(categoryMapper.selectById(3)).thenReturn(category(3, "计算机基础"));
        when(courseMapper.selectCount(any())).thenReturn(2L);
        CourseCategoryDeleteActionExecutor executor = new CourseCategoryDeleteActionExecutor(categoryMapper, courseMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> executor.execute(
                new AgentActionExecutionContext(
                        loginAs("admin", 1),
                        Map.of("categoryId", 3),
                        ""
                )
        ));

        assertEquals("该分类下还有课程，请设置 cascade=true 后再删除", exception.getMessage());
        verify(courseMapper, never()).delete(any());
        verify(categoryMapper, never()).deleteById(org.mockito.ArgumentMatchers.<java.io.Serializable>any());
    }

    @Test
    void deleteCascadesCoursesWhenExplicitlyRequested() {
        when(categoryMapper.selectById(3)).thenReturn(category(3, "计算机基础"));
        when(courseMapper.selectCount(any())).thenReturn(2L);
        when(courseMapper.delete(any())).thenReturn(2);
        when(categoryMapper.deleteById((java.io.Serializable) 3)).thenReturn(1);
        CourseCategoryDeleteActionExecutor executor = new CourseCategoryDeleteActionExecutor(categoryMapper, courseMapper);

        AgentActionExecutionResult result = executor.execute(new AgentActionExecutionContext(
                loginAs("admin", 1),
                Map.of("categoryId", 3, "cascade", true),
                ""
        ));

        assertTrue(result.success());
        assertEquals(AgentActionRiskLevel.HIGH, executor.riskLevel());
        assertEquals(CourseCategoryDeletePlanTool.CONFIRMATION_TEXT, executor.confirmationText());
        assertEquals(2, result.data().get("deletedCourses"));
        assertEquals(Boolean.TRUE, result.data().get("cascade"));
        verify(courseMapper).delete(any());
        verify(categoryMapper).deleteById((java.io.Serializable) 3);
    }

    private AuthUtils.LoginUser loginAs(String roleCode, Integer userId) {
        AuthUtils.LoginUser user = new AuthUtils.LoginUser();
        user.setUserId(userId);
        user.setUsername(roleCode + userId);
        user.setRoleCode(roleCode);
        return user;
    }

    private CourseCategory category(Integer id, String name) {
        CourseCategory category = new CourseCategory();
        category.setId(id);
        category.setName(name);
        category.setDescription(name + "方向");
        category.setSortOrder(1);
        return category;
    }
}
