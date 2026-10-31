package com.cupk.aiacademy.agent.action.homework;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.homework.HomeworkDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.pojo.Homework;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HomeworkDeleteActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "homework.delete";

    private final HomeworkMapper homeworkMapper;

    public HomeworkDeleteActionExecutor(HomeworkMapper homeworkMapper) {
        this.homeworkMapper = homeworkMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "删除作业";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return HomeworkDeletePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Integer homeworkId = requiredInteger(context.parameters(), "homeworkId", "homework_id", "assignmentId", "id");
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }
        if (!isAdmin(user) && (homework.getTeacherId() == null || !homework.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能删除当前登录教师发布的作业");
        }

        int row = homeworkMapper.deleteById(homeworkId);
        if (row <= 0) {
            throw new BusinessException("删除作业失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("homeworkId", homework.getId());
        data.put("classId", homework.getClassId());
        data.put("teacherId", homework.getTeacherId());
        data.put("title", homework.getTitle());
        data.put("fullScore", homework.getFullScore());
        data.put("startTime", formatDateTime(homework.getStartTime()));
        data.put("deadline", formatDateTime(homework.getDeadline()));
        data.put("status", homework.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "作业已删除",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以删除作业");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        Integer parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(keys[0] + " 必须是整数");
            }
        }
        if (parsed <= 0) {
            throw new BusinessException(keys[0] + " 不合法");
        }
        return parsed;
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

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
