package com.cupk.aiacademy.agent.action.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.ClassStudentMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.ClassStudent;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ClassStudentAddActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "class.student.add";

    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final UserMapper userMapper;

    public ClassStudentAddActionExecutor(ClassStudentMapper classStudentMapper,
                                         TeachingClassMapper teachingClassMapper,
                                         UserMapper userMapper) {
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "添加班级学生";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer classId = requiredInteger(parameters, "classId", "class_id", "teachingClassId");
        TeachingClass teachingClass = requireManagedClass(classId, user);
        Integer studentId = requiredInteger(parameters, "studentId", "student_id", "userId");
        if (userMapper.selectById(studentId) == null) {
            throw new BusinessException("学生用户不存在");
        }
        ensureNotDuplicate(classId, studentId);

        Integer status = optionalInteger(parameters, "status");
        if (status == null) {
            status = 1;
        }
        validateStatus(status);

        ClassStudent classStudent = new ClassStudent();
        classStudent.setClassId(classId);
        classStudent.setStudentId(studentId);
        classStudent.setStatus(status);
        classStudent.setJoinTime(LocalDateTime.now());
        int row = classStudentMapper.insert(classStudent);
        if (row <= 0) {
            throw new BusinessException("添加班级学生失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classStudentId", classStudent.getId());
        data.put("classId", classStudent.getClassId());
        data.put("studentId", classStudent.getStudentId());
        data.put("teacherId", teachingClass.getTeacherId());
        data.put("status", classStudent.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "班级学生已添加",
                data,
                LocalDateTime.now()
        );
    }

    private TeachingClass requireManagedClass(Integer classId, AuthUtils.LoginUser user) {
        TeachingClass teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException("教学班不存在");
        }
        if (!isAdmin(user) && (teachingClass.getTeacherId() == null
                || !teachingClass.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能给当前登录教师负责的教学班添加学生");
        }
        return teachingClass;
    }

    private void ensureNotDuplicate(Integer classId, Integer studentId) {
        QueryWrapper<ClassStudent> query = new QueryWrapper<>();
        query.eq("class_id", classId).eq("student_id", studentId);
        Long count = classStudentMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException("该学生已加入该教学班");
        }
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以添加班级学生");
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
