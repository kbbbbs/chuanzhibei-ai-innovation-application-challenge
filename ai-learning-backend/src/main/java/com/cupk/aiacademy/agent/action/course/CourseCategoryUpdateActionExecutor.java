package com.cupk.aiacademy.agent.action.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.pojo.CourseCategory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CourseCategoryUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "course.category.update";

    private final CourseCategoryMapper categoryMapper;

    public CourseCategoryUpdateActionExecutor(CourseCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "修改课程分类";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        requireAdmin(context.user());
        Map<String, Object> parameters = context.parameters();
        CourseCategory category = resolveCategory(parameters);

        Map<String, Object> oldValues = snapshot(category);
        boolean changed = applyUpdates(category, parameters);
        if (!changed) {
            throw new BusinessException("至少需要提供一个可修改的课程分类字段");
        }

        category.setUpdateTime(LocalDateTime.now());
        int row = categoryMapper.updateById(category);
        if (row <= 0) {
            throw new BusinessException("修改课程分类失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", category.getId());
        data.put("oldName", oldValues.get("name"));
        data.put("newName", category.getName());
        data.put("oldDescription", oldValues.get("description"));
        data.put("newDescription", category.getDescription());
        data.put("oldSortOrder", oldValues.get("sortOrder"));
        data.put("newSortOrder", category.getSortOrder());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程分类已修改",
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
        String categoryName = optionalString(parameters,
                new String[]{"categoryName", "category_name", "originalName", "original_name",
                        "oldName", "old_name", "currentName", "current_name"}, 100);
        if (categoryName == null || categoryName.isBlank()) {
            throw new BusinessException("categoryId 或 categoryName 不能为空");
        }
        List<CourseCategory> categories = categoryMapper.selectList(
                new QueryWrapper<CourseCategory>().eq("name", categoryName.trim()));
        if (categories == null || categories.isEmpty()) {
            throw new BusinessException("课程分类不存在：" + categoryName);
        }
        if (categories.size() > 1) {
            throw new BusinessException("课程分类名称不唯一，请补充 categoryId：" + categoryName);
        }
        return categories.get(0);
    }

    private boolean applyUpdates(CourseCategory category, Map<String, Object> parameters) {
        boolean changed = false;
        if (hasAnyKey(parameters, "name", "newName", "new_name")) {
            String name = requiredString(parameters, new String[]{"name", "newName", "new_name"}, 100);
            if (!name.equals(category.getName()) && existsByName(name, category.getId())) {
                throw new BusinessException("课程分类名称已存在：" + name);
            }
            category.setName(name);
            changed = true;
        }
        if (hasAnyKey(parameters, "description", "desc")) {
            category.setDescription(optionalString(parameters, new String[]{"description", "desc"}, 255));
            changed = true;
        }
        if (hasAnyKey(parameters, "sortOrder", "sort_order")) {
            category.setSortOrder(fixedSortOrder(optionalInteger(parameters, "sortOrder", "sort_order")));
            changed = true;
        }
        return changed;
    }

    private boolean existsByName(String name, Integer excludingId) {
        QueryWrapper<CourseCategory> query = new QueryWrapper<>();
        query.eq("name", name.trim());
        if (excludingId != null && excludingId > 0) {
            query.ne("id", excludingId);
        }
        Long count = categoryMapper.selectCount(query);
        return count != null && count > 0;
    }

    private Map<String, Object> snapshot(CourseCategory category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", category.getName());
        values.put("description", category.getDescription());
        values.put("sortOrder", category.getSortOrder());
        return values;
    }

    private void requireAdmin(AuthUtils.LoginUser user) {
        if (user == null || !"admin".equals(user.getRoleCode())) {
            throw new BusinessException("只有管理员可以管理课程分类");
        }
    }

    private String requiredString(Map<String, Object> parameters, String[] keys, int maxLength) {
        String value = optionalString(parameters, keys, maxLength);
        if (value == null || value.isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
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

    private Integer fixedSortOrder(Integer sortOrder) {
        return sortOrder == null || sortOrder <= 0 ? null : sortOrder;
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

    private boolean hasAnyKey(Map<String, Object> parameters, String... keys) {
        if (parameters == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
