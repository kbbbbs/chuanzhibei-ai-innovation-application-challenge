package com.cupk.aiacademy.agent.action.course;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.course.TeachingClassStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.controller.TeachingClassController;
import com.cupk.aiacademy.dto.TeachingClassDto;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TeachingClassStatusActionExecutor implements AgentActionExecutor {
    private final TeachingClassController classMaintenance;

    private static final String ACTION_CODE = "teaching.class.status";

    private final TeachingClassMapper teachingClassMapper;

    public TeachingClassStatusActionExecutor(TeachingClassMapper teachingClassMapper, TeachingClassController classMaintenance) {
        this.classMaintenance = classMaintenance;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "变更教学班状态";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return TeachingClassStatusPlanTool.CONFIRMATION_TEXT;
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
            throw new BusinessException("只能变更当前登录教师负责的教学班状态");
        }

        Integer status = requiredStatus(parameters, "status");
        validateStatus(status);

        Integer oldStatus = teachingClass.getStatus();
        teachingClass.setStatus(status);
        teachingClass.setUpdateTime(LocalDateTime.now());
        Result saved = classMaintenance.update(classId, TeachingClassDto.fromEntity(teachingClass));
        if (!"200".equals(saved.getCode())) throw new BusinessException(saved.getMsg());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classId", teachingClass.getId());
        data.put("courseId", teachingClass.getCourseId());
        data.put("teacherId", teachingClass.getTeacherId());
        data.put("className", teachingClass.getClassName());
        data.put("oldStatus", oldStatus);
        data.put("newStatus", teachingClass.getStatus());
        data.put("reason", optionalString(parameters, "reason"));
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "教学班状态已变更",
                data,
                LocalDateTime.now()
        );
    }

    private void requireAdmin(AuthUtils.LoginUser user) {
        if (!isAdmin(user)) {
            throw new BusinessException("只有管理员可以变更教学班状态");
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
