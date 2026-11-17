package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.controller.TeachingClassController;
import com.cupk.aiacademy.dto.TeachingClassDto;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TeachingClassCreateActionExecutor implements AgentActionExecutor {
    private final TeachingClassController classMaintenance;

    private static final String ACTION_CODE = "teaching.class.create";

    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public TeachingClassCreateActionExecutor(CourseMapper courseMapper,
                                             UserMapper userMapper, TeachingClassController classMaintenance) {
        this.classMaintenance = classMaintenance;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "创建教学班";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer courseId = requiredInteger(parameters, "courseId", "course_id");
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能在当前登录教师负责的课程下创建教学班");
        }

        Integer teacherId = optionalInteger(parameters, "teacherId", "teacher_id");
        if (teacherId == null) {
            teacherId = isAdmin(user) && course.getTeacherId() != null ? course.getTeacherId() : user.getUserId();
        }
        if (!isAdmin(user) && !teacherId.equals(user.getUserId())) {
            throw new BusinessException("只能以当前登录教师身份创建教学班");
        }
        if (userMapper.selectById(teacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }

        Integer status = optionalInteger(parameters, "status");
        if (status == null) {
            status = 1;
        }
        validateStatus(status);

        LocalDateTime now = LocalDateTime.now();
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setCourseId(courseId);
        teachingClass.setTeacherId(teacherId);
        teachingClass.setClassName(requiredString(parameters, "className", "class_name", 100));
        teachingClass.setSemester(optionalString(parameters, new String[]{"semester"}, 50));
        teachingClass.setDescription(optionalString(parameters, new String[]{"description"}, 255));
        teachingClass.setStatus(status);
        teachingClass.setCreateTime(now);
        teachingClass.setUpdateTime(now);

        Result saved = classMaintenance.add(TeachingClassDto.fromEntity(teachingClass));
        if (!"200".equals(saved.getCode())) throw new BusinessException(saved.getMsg());
        teachingClass = (TeachingClass) saved.getData();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classId", teachingClass.getId());
        data.put("courseId", teachingClass.getCourseId());
        data.put("teacherId", teachingClass.getTeacherId());
        data.put("className", teachingClass.getClassName());
        data.put("semester", teachingClass.getSemester());
        data.put("status", teachingClass.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "教学班已创建",
                data,
                LocalDateTime.now()
        );
    }

    private void requireAdmin(AuthUtils.LoginUser user) {
        if (!isAdmin(user)) {
            throw new BusinessException("只有管理员可以创建教学班");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("教学班状态只能是 0 或 1");
        }
    }

    private String requiredString(Map<String, Object> parameters, String key, String alias, int maxLength) {
        String value = optionalString(parameters, new String[]{key, alias}, maxLength);
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
