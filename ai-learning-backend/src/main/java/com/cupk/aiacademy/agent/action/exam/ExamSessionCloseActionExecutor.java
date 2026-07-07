package com.cupk.aiacademy.agent.action.exam;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.exam.ExamSessionClosePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.service.ExamService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ExamSessionCloseActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.session.close";

    private final ExamService examService;

    public ExamSessionCloseActionExecutor(ExamService examService) {
        this.examService = examService;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "关闭考试安排";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return ExamSessionClosePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Long examSessionId = requiredLong(context.parameters(), "examSessionId", "exam_session_id", "examId", "id");
        ExamResponseDTO existing = examService.getById(examSessionId);
        if (!isAdmin(user) && (existing.getTeacherId() == null || !existing.getTeacherId().equals(user.getUserId().longValue()))) {
            throw new BusinessException("只能关闭当前登录教师创建的考试");
        }

        ExamResponseDTO response = examService.close(examSessionId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examSessionId", response.getId());
        data.put("classId", response.getClassId());
        data.put("teacherId", response.getTeacherId());
        data.put("paperId", response.getPaperId());
        data.put("name", response.getName());
        data.put("status", response.getStatus() == null ? null : response.getStatus().name());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "考试已关闭",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以关闭考试");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private Long requiredLong(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        Long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(keys[0] + " 必须是整数");
            }
        }
        if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
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
}
