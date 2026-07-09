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
import java.util.Map;

@Component
public class CourseCategoryCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "course.category.create";

    private final CourseCategoryMapper categoryMapper;

    public CourseCategoryCreateActionExecutor(CourseCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "创建课程分类";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        requireAdmin(context.user());
        Map<String, Object> parameters = context.parameters();
        String name = requiredString(parameters, "name", 100);
        if (existsByName(name, null)) {
            throw new BusinessException("课程分类名称已存在：" + name);
        }

        LocalDateTime now = LocalDateTime.now();
        CourseCategory category = new CourseCategory();
        category.setName(name);
        category.setDescription(optionalString(parameters, new String[]{"description", "desc"}, 255));
        category.setSortOrder(fixedSortOrder(optionalInteger(parameters, "sortOrder", "sort_order")));
        category.setCreateTime(now);
        category.setUpdateTime(now);

        int row = categoryMapper.insert(category);
        if (row <= 0) {
            throw new BusinessException("创建课程分类失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", category.getId());
        data.put("name", category.getName());
        data.put("description", category.getDescription());
        data.put("sortOrder", category.getSortOrder());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程分类已创建",
                data,
                LocalDateTime.now()
        );
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

    private void requireAdmin(AuthUtils.LoginUser user) {
        if (user == null || !"admin".equals(user.getRoleCode())) {
            throw new BusinessException("只有管理员可以管理课程分类");
        }
    }

    private String requiredString(Map<String, Object> parameters, String key, int maxLength) {
        String value = optionalString(parameters, new String[]{key}, maxLength);
        if (value == null || value.isBlank()) {
            throw new BusinessException(key + " 不能为空");
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
}
