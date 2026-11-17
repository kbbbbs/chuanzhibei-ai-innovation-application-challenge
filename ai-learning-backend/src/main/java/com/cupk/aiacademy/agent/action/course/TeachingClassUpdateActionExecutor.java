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
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TeachingClassUpdateActionExecutor implements AgentActionExecutor {
    private final TeachingClassController classMaintenance;

    private static final String ACTION_CODE = "teaching.class.update";

    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public TeachingClassUpdateActionExecutor(TeachingClassMapper teachingClassMapper,
                                             CourseMapper courseMapper,
                                             UserMapper userMapper, TeachingClassController classMaintenance) {
        this.classMaintenance = classMaintenance;
        this.teachingClassMapper = teachingClassMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "修改教学班";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer classId = requiredInteger(parameters, "classId", "class_id", "teachingClassId", "id");
        TeachingClass teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException("教学班不存在");
        }
        if (!isAdmin(user) && (teachingClass.getTeacherId() == null
                || !teachingClass.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能修改当前登录教师负责的教学班");
        }

        Map<String, Object> oldValues = snapshot(teachingClass);
        TeachingClass edited = new TeachingClass();
        org.springframework.beans.BeanUtils.copyProperties(teachingClass, edited);
        teachingClass = edited;
        boolean changed = applyUpdates(teachingClass, parameters, user);
        if (!changed) {
            throw new BusinessException("至少需要提供一个可修改的教学班字段");
        }

        teachingClass.setUpdateTime(LocalDateTime.now());
        Result saved = classMaintenance.update(classId, TeachingClassDto.fromEntity(teachingClass));
        if (!"200".equals(saved.getCode())) throw new BusinessException(saved.getMsg());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classId", teachingClass.getId());
        data.put("oldCourseId", oldValues.get("courseId"));
        data.put("newCourseId", teachingClass.getCourseId());
        data.put("oldTeacherId", oldValues.get("teacherId"));
        data.put("newTeacherId", teachingClass.getTeacherId());
        data.put("oldClassName", oldValues.get("className"));
        data.put("newClassName", teachingClass.getClassName());
        data.put("oldSemester", oldValues.get("semester"));
        data.put("newSemester", teachingClass.getSemester());
        data.put("oldStatus", oldValues.get("status"));
        data.put("newStatus", teachingClass.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "教学班已修改",
                data,
                LocalDateTime.now()
        );
    }

    private boolean applyUpdates(TeachingClass teachingClass, Map<String, Object> parameters, AuthUtils.LoginUser user) {
        boolean changed = false;
        if (hasAnyKey(parameters, "courseId", "course_id")) {
            Integer courseId = requiredInteger(parameters, "courseId", "course_id");
            Course course = courseMapper.selectById(courseId);
            if (course == null) {
                throw new BusinessException("课程不存在");
            }
            if (!isAdmin(user) && (course.getTeacherId() == null || !course.getTeacherId().equals(user.getUserId()))) {
                throw new BusinessException("只能把教学班调整到当前登录教师负责的课程");
            }
            teachingClass.setCourseId(courseId);
            changed = true;
        }
        if (hasAnyKey(parameters, "teacherId", "teacher_id")) {
            Integer teacherId = requiredInteger(parameters, "teacherId", "teacher_id");
            if (!isAdmin(user) && !teacherId.equals(user.getUserId())) {
                throw new BusinessException("只能以当前登录教师身份修改教学班");
            }
            if (userMapper.selectById(teacherId) == null) {
                throw new BusinessException("教师用户不存在");
            }
            teachingClass.setTeacherId(teacherId);
            changed = true;
        }
        if (hasAnyKey(parameters, "className", "class_name")) {
            teachingClass.setClassName(requiredString(parameters, "className", "class_name", 100));
            changed = true;
        }
        if (hasAnyKey(parameters, "semester")) {
            teachingClass.setSemester(optionalString(parameters, new String[]{"semester"}, 50));
            changed = true;
        }
        if (hasAnyKey(parameters, "description")) {
            teachingClass.setDescription(optionalString(parameters, new String[]{"description"}, 255));
            changed = true;
        }
        return changed;
    }

    private Map<String, Object> snapshot(TeachingClass teachingClass) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("courseId", teachingClass.getCourseId());
        values.put("teacherId", teachingClass.getTeacherId());
        values.put("className", teachingClass.getClassName());
        values.put("semester", teachingClass.getSemester());
        values.put("description", teachingClass.getDescription());
        values.put("status", teachingClass.getStatus());
        return values;
    }

    private void requireAdmin(AuthUtils.LoginUser user) {
        if (!isAdmin(user)) {
            throw new BusinessException("只有管理员可以修改教学班");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
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
