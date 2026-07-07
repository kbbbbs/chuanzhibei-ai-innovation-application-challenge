package com.cupk.aiacademy.agent.action.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.ClassStudentStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ClassStudentStatusActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "class.student.status";

    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;

    public ClassStudentStatusActionExecutor(ClassStudentMapper classStudentMapper,
                                            TeachingClassMapper teachingClassMapper) {
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "变更班级学生状态";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return ClassStudentStatusPlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        ClassStudent classStudent = resolveClassStudent(parameters);
        if (classStudent == null) {
            throw new BusinessException("学生名单记录不存在");
        }
        TeachingClass teachingClass = requireManagedClass(classStudent.getClassId(), user);
        Integer status = requiredStatus(parameters, "status");
        validateStatus(status);

        Integer oldStatus = classStudent.getStatus();
        classStudent.setStatus(status);
        int row = classStudentMapper.updateById(classStudent);
        if (row <= 0) {
            throw new BusinessException("变更班级学生状态失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classStudentId", classStudent.getId());
        data.put("classId", classStudent.getClassId());
        data.put("studentId", classStudent.getStudentId());
        data.put("teacherId", teachingClass.getTeacherId());
        data.put("oldStatus", oldStatus);
        data.put("newStatus", classStudent.getStatus());
        data.put("reason", optionalString(parameters, "reason"));
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "班级学生状态已变更",
                data,
                LocalDateTime.now()
        );
    }

    private ClassStudent resolveClassStudent(Map<String, Object> parameters) {
        Integer classStudentId = optionalInteger(parameters, "classStudentId", "class_student_id", "id");
        if (classStudentId != null) {
            return classStudentMapper.selectById(classStudentId);
        }
        Integer classId = requiredInteger(parameters, "classId", "class_id", "teachingClassId");
        Integer studentId = requiredInteger(parameters, "studentId", "student_id", "userId");
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("class_id", classId).eq("student_id", studentId);
        return classStudentMapper.selectOne(query);
    }

    private TeachingClass requireManagedClass(Integer classId, AuthUtils.LoginUser user) {
        TeachingClass teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException("教学班不存在");
        }
        if (!isAdmin(user) && (teachingClass.getTeacherId() == null
                || !teachingClass.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能变更当前登录教师负责教学班的学生状态");
        }
        return teachingClass;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以变更班级学生状态");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException("班级学生状态只能是 1 或 2");
        }
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null || value <= 0) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private Integer requiredStatus(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
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
