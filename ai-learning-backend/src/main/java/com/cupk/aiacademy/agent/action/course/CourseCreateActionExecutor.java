package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CourseCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "course.create";

    private final CourseMapper courseMapper;
    private final CourseCategoryMapper categoryMapper;
    private final UserMapper userMapper;

    public CourseCreateActionExecutor(CourseMapper courseMapper,
                                      CourseCategoryMapper categoryMapper,
                                      UserMapper userMapper) {
        this.courseMapper = courseMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "创建课程";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        String title = requiredString(parameters, "title", 100);
        Integer categoryId = requiredInteger(parameters, "categoryId", "category_id");
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("课程分类不存在");
        }

        Integer teacherId = optionalInteger(parameters, "teacherId", "teacher_id");
        if (teacherId == null) {
            teacherId = user.getUserId();
        }
        if (!teacherId.equals(user.getUserId()) && !isAdmin(user)) {
            throw new BusinessException("只能以当前登录教师身份创建课程");
        }
        if (userMapper.selectById(teacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }

        Integer status = optionalInteger(parameters, "status");
        if (status == null) {
            status = 1;
        }
        requireValidStatus(status);

        LocalDateTime now = LocalDateTime.now();
        Course course = new Course();
        course.setTitle(title);
        course.setDescription(optionalString(parameters, "description", 1000));
        course.setCover(optionalString(parameters, "cover", 255));
        course.setCoverUrl(optionalString(parameters, "coverUrl", "cover_url", 255));
        course.setTeacher(optionalString(parameters, "teacher", 50));
        course.setDifficulty(optionalString(parameters, "difficulty", 20));
        course.setCategoryId(categoryId);
        course.setTeacherId(teacherId);
        course.setStudentCount(nonNegativeOrDefault(parameters, 0, "studentCount", "student_count"));
        course.setViewCount(nonNegativeOrDefault(parameters, 0, "viewCount", "view_count"));
        course.setRating(optionalRating(parameters, "rating"));
        course.setStatus(status);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);

        int row = courseMapper.insert(course);
        if (row <= 0) {
            throw new BusinessException("创建课程失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", course.getId());
        data.put("title", course.getTitle());
        data.put("categoryId", course.getCategoryId());
        data.put("teacherId", course.getTeacherId());
        data.put("difficulty", course.getDifficulty());
        data.put("status", course.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程已创建",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以创建课程");
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

    private String optionalString(Map<String, Object> parameters, String key1, String key2, int maxLength) {
        return optionalString(parameters, new String[]{key1, key2}, maxLength);
    }

    private String optionalString(Map<String, Object> parameters, String... keys) {
        return optionalString(parameters, keys, 0);
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

    private Integer nonNegativeOrDefault(Map<String, Object> parameters, Integer defaultValue, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            return defaultValue;
        }
        if (value < 0) {
            throw new BusinessException(keys[0] + " 不能小于 0");
        }
        return value;
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

    private void requireValidStatus(Integer status) {
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException("课程状态只能是 0、1 或 2");
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
