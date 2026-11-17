package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.query.AgentWriteEntityLocator;
import com.cupk.aiacademy.agent.tool.course.CourseDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.pojo.Course;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CourseDeleteActionExecutor implements AgentActionExecutor {
    @org.springframework.beans.factory.annotation.Autowired
    private com.cupk.aiacademy.controller.CourseController courseMaintenance;
    private static final String ACTION_CODE = "course.delete";

    private final CourseMapper courseMapper;
    private final AgentWriteEntityLocator entityLocator;

    public CourseDeleteActionExecutor(CourseMapper courseMapper,
                                      AgentWriteEntityLocator entityLocator) {
        this.courseMapper = courseMapper;
        this.entityLocator = entityLocator;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "删除课程";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return CourseDeletePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Course course = resolveCourse(parameters);
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能删除当前登录教师负责的课程");
        }

        var deleted = courseMaintenance.delete(course.getId());
        if (!"200".equals(deleted.getCode())) throw new BusinessException(deleted.getMsg());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", course.getId());
        data.put("title", course.getTitle());
        data.put("teacherId", course.getTeacherId());
        data.put("reason", optionalString(parameters, "reason"));
        data.put("deletedAt", LocalDateTime.now());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "课程已删除",
                data,
                LocalDateTime.now()
        );
    }

    private Course resolveCourse(Map<String, Object> parameters) {
        Integer courseId = optionalInteger(parameters, "courseId", "course_id", "id");
        String title = optionalString(parameters, "title", "courseTitle", "courseName");
        String teacher = optionalString(parameters, "teacher");
        String difficulty = optionalString(parameters, "difficulty");
        Integer categoryId = optionalInteger(parameters, "categoryId", "category_id");
        return entityLocator.locateCourse(courseId, title, teacher, difficulty, categoryId);
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以删除课程");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
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

    private String optionalString(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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
