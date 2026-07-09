package com.cupk.aiacademy.agent.action.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.CourseCategoryDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CourseCategoryDeleteActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "course.category.delete";

    private final CourseCategoryMapper categoryMapper;
    private final CourseMapper courseMapper;

    public CourseCategoryDeleteActionExecutor(CourseCategoryMapper categoryMapper, CourseMapper courseMapper) {
        this.categoryMapper = categoryMapper;
        this.courseMapper = courseMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "删除课程分类";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return CourseCategoryDeletePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        requireAdmin(context.user());
        Map<String, Object> parameters = context.parameters();
        CourseCategory category = resolveCategory(parameters);
        boolean cascade = Boolean.TRUE.equals(optionalBoolean(parameters, "cascade", "级联删除"))
                || containsCascadeIntent(optionalString(parameters, new String[]{"rawRequest"}, 0));

        QueryWrapper<Course> countQuery = new QueryWrapper<>();
        countQuery.eq("category_id", category.getId());
        Long courseCountValue = courseMapper.selectCount(countQuery);
        int courseCount = courseCountValue == null ? 0 : Math.toIntExact(courseCountValue);
        if (courseCount > 0 && !cascade) {
            throw new BusinessException("该分类下还有课程，请设置 cascade=true 后再删除");
        }

        int deletedCourses = 0;
        if (courseCount > 0) {
            QueryWrapper<Course> deleteCourseQuery = new QueryWrapper<>();
            deleteCourseQuery.eq("category_id", category.getId());
            deletedCourses = courseMapper.delete(deleteCourseQuery);
        }

        int row = categoryMapper.deleteById(category.getId());
        if (row <= 0) {
            throw new BusinessException("删除课程分类失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", category.getId());
        data.put("name", category.getName());
        data.put("courseCount", courseCount);
        data.put("cascade", cascade);
        data.put("deletedCourses", deletedCourses);
        data.put("deletedAt", LocalDateTime.now());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程分类已删除",
                data,
                LocalDateTime.now()
        );
    }

    private CourseCategory resolveCategory(Map<String, Object> parameters) {
        Integer categoryId = optionalInteger(parameters, "categoryId", "category_id", "id");
        if (categoryId != null) {
            CourseCategory category = categoryMapper.selectById(categoryId);
            if (category == null) {
                throw new BusinessException("课程分类不存在");
            }
            return category;
        }
        String name = optionalString(parameters,
                new String[]{"name", "categoryName", "category_name", "课程分类", "分类名称", "分类"}, 100);
        if (name == null || name.isBlank()) {
            throw new BusinessException("categoryId 或 name 不能为空");
        }
        List<CourseCategory> categories = categoryMapper.selectList(
                new QueryWrapper<CourseCategory>().eq("name", name.trim()));
        if (categories == null || categories.isEmpty()) {
            throw new BusinessException("课程分类不存在：" + name);
        }
        if (categories.size() > 1) {
            throw new BusinessException("课程分类名称不唯一，请补充 categoryId：" + name);
        }
        return categories.get(0);
    }

    private void requireAdmin(AuthUtils.LoginUser user) {
        if (user == null || !"admin".equals(user.getRoleCode())) {
            throw new BusinessException("只有管理员可以管理课程分类");
        }
    }

    private Boolean optionalBoolean(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
        if (List.of("true", "1", "yes", "y", "是", "开启", "启用").contains(text)) {
            return true;
        }
        if (List.of("false", "0", "no", "n", "否", "关闭", "停用").contains(text)) {
            return false;
        }
        throw new BusinessException(keys[0] + " 必须是布尔值");
    }

    private boolean containsCascadeIntent(String rawRequest) {
        return rawRequest != null && (rawRequest.contains("级联")
                || rawRequest.contains("连同课程")
                || rawRequest.contains("课程一起")
                || rawRequest.contains("一起删除")
                || rawRequest.contains("一起删"));
    }

    private String optionalString(Map<String, Object> parameters, String[] keys, int maxLength) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (maxLength > 0 && text.length() > maxLength) {
            throw new BusinessException(keys[0] + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    private Integer optionalInteger(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private Object firstValue(Map<String, Object> parameters, String... keys) {
        if (parameters == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return parameters.get(key);
            }
        }
        return null;
    }
}
