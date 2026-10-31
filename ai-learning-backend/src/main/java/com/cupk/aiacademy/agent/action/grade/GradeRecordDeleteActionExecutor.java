package com.cupk.aiacademy.agent.action.grade;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordDeletePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.pojo.GradeRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GradeRecordDeleteActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "grade.record.delete";

    private final GradeRecordMapper gradeRecordMapper;

    public GradeRecordDeleteActionExecutor(GradeRecordMapper gradeRecordMapper) {
        this.gradeRecordMapper = gradeRecordMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "删除成绩记录";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return GradeRecordDeletePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Integer gradeRecordId = requiredInteger(context.parameters(), "gradeRecordId", "grade_record_id", "gradeId", "id");
        GradeRecord grade = gradeRecordMapper.selectById(gradeRecordId);
        if (grade == null) {
            throw new BusinessException("成绩记录不存在");
        }
        if (!isAdmin(user) && (grade.getTeacherId() == null || !grade.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能删除当前登录教师负责的成绩记录");
        }

        int row = gradeRecordMapper.deleteById(gradeRecordId);
        if (row <= 0) {
            throw new BusinessException("删除成绩失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gradeRecordId", grade.getId());
        data.put("studentId", grade.getStudentId());
        data.put("classId", grade.getClassId());
        data.put("courseId", grade.getCourseId());
        data.put("teacherId", grade.getTeacherId());
        data.put("gradeName", grade.getGradeName());
        data.put("sourceType", grade.getSourceType());
        data.put("score", grade.getScore());
        data.put("fullScore", grade.getFullScore());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "成绩记录已删除",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以删除成绩");
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
}
