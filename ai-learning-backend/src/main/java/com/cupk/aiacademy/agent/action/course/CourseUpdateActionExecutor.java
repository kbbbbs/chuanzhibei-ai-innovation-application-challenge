package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CourseUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "course.update";

    private final CourseMapper courseMapper;
    private final CourseCategoryMapper categoryMapper;

    public CourseUpdateActionExecutor(CourseMapper courseMapper, CourseCategoryMapper categoryMapper) {
        this.courseMapper = courseMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "修改课程";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer courseId = requiredInteger(parameters, "courseId", "course_id", "id");
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能修改当前登录教师负责的课程");
        }

        Map<String, Object> oldValues = snapshot(course);
        boolean changed = applyUpdates(course, parameters);
        if (!changed) {
            throw new BusinessException("至少需要提供一个可修改的课程字段");
        }

        course.setUpdatedAt(LocalDateTime.now());
        int row = courseMapper.updateById(course);
        if (row <= 0) {
            throw new BusinessException("修改课程失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", course.getId());
        data.put("oldTitle", oldValues.get("title"));
        data.put("newTitle", course.getTitle());
        data.put("oldCategoryId", oldValues.get("categoryId"));
        data.put("newCategoryId", course.getCategoryId());
        data.put("oldDifficulty", oldValues.get("difficulty"));
        data.put("newDifficulty", course.getDifficulty());
        data.put("oldRating", oldValues.get("rating"));
        data.put("newRating", course.getRating());
        data.put("teacherId", course.getTeacherId());
        data.put("status", course.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程已修改",
                data,
                LocalDateTime.now()
        );
    }

    private boolean applyUpdates(Course course, Map<String, Object> parameters) {
        boolean changed = false;
        if (hasAnyKey(parameters, "title")) {
            course.setTitle(requiredString(parameters, "title", 100));
            changed = true;
        }
        if (hasAnyKey(parameters, "description")) {
            course.setDescription(optionalString(parameters, "description", 1000));
            changed = true;
        }
        if (hasAnyKey(parameters, "cover")) {
            course.setCover(optionalString(parameters, "cover", 255));
            changed = true;
        }
        if (hasAnyKey(parameters, "coverUrl", "cover_url")) {
            course.setCoverUrl(optionalString(parameters, new String[]{"coverUrl", "cover_url"}, 255));
            changed = true;
        }
        if (hasAnyKey(parameters, "teacher")) {
            course.setTeacher(optionalString(parameters, "teacher", 50));
            changed = true;
        }
        if (hasAnyKey(parameters, "difficulty")) {
            course.setDifficulty(optionalString(parameters, "difficulty", 20));
            changed = true;
        }
        if (hasAnyKey(parameters, "categoryId", "category_id")) {
            Integer categoryId = requiredInteger(parameters, "categoryId", "category_id");
            if (categoryMapper.selectById(categoryId) == null) {
                throw new BusinessException("课程分类不存在");
            }
            course.setCategoryId(categoryId);
            changed = true;
        }
        if (hasAnyKey(parameters, "rating")) {
            course.setRating(optionalRating(parameters, "rating"));
            changed = true;
        }
        return changed;
    }

    private Map<String, Object> snapshot(Course course) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", course.getTitle());
        values.put("description", course.getDescription());
        values.put("cover", course.getCover());
        values.put("coverUrl", course.getCoverUrl());
        values.put("teacher", course.getTeacher());
        values.put("difficulty", course.getDifficulty());
        values.put("categoryId", course.getCategoryId());
        values.put("rating", course.getRating());
        return values;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以修改课程");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private String requiredString(Map<String, Object> parameters, String key, int maxLength) {
        String value = optionalString(parameters, key, maxLength);
        if (value == null || value.isBlank()) {
            throw new BusinessException(key + " 不能为空");
        }
        return value;
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null || value <= 0) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private String optionalString(Map<String, Object> parameters, String key, int maxLength) {
        return optionalString(parameters, new String[]{key}, maxLength);
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

    private BigDecimal optionalRating(Map<String, Object> parameters, String... keys) {
        BigDecimal rating = optionalDecimal(parameters, keys);
        if (rating == null) {
            return null;
        }
        if (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BusinessException("rating 必须在 0 到 5 之间");
        }
        return rating;
    }

    private BigDecimal optionalDecimal(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是数字");
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
