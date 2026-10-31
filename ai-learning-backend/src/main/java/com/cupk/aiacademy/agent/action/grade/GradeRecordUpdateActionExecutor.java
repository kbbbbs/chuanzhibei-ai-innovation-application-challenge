package com.cupk.aiacademy.agent.action.grade;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.grade.GradeRecordUpdatePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.pojo.GradeRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GradeRecordUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "grade.record.update";

    private final GradeRecordMapper gradeRecordMapper;

    public GradeRecordUpdateActionExecutor(GradeRecordMapper gradeRecordMapper) {
        this.gradeRecordMapper = gradeRecordMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "修改成绩记录";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return GradeRecordUpdatePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer gradeRecordId = requiredInteger(parameters, "gradeRecordId", "grade_record_id", "gradeId", "id");
        GradeRecord grade = gradeRecordMapper.selectById(gradeRecordId);
        if (grade == null) {
            throw new BusinessException("成绩记录不存在");
        }
        if (!isAdmin(user) && (grade.getTeacherId() == null || !grade.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能修改当前登录教师负责的成绩记录");
        }

        BigDecimal oldScore = grade.getScore();
        BigDecimal oldFullScore = grade.getFullScore();
        String oldComment = grade.getComment();
        String oldGradeName = grade.getGradeName();

        BigDecimal newScore = requiredDecimal(parameters, "score");
        BigDecimal newFullScore = optionalDecimal(parameters, "fullScore", "full_score");
        if (newFullScore != null) {
            requirePositive(newFullScore, "fullScore");
            grade.setFullScore(newFullScore);
        }
        BigDecimal effectiveFullScore = grade.getFullScore();
        if (effectiveFullScore != null && newScore.compareTo(effectiveFullScore) > 0) {
            throw new BusinessException("分数不能超过满分");
        }
        grade.setScore(newScore);

        String comment = optionalString(parameters, "comment", "gradeComment", "grade_comment");
        if (comment != null) {
            if (comment.length() > 255) {
                throw new BusinessException("comment 长度不能超过 255");
            }
            grade.setComment(comment);
        }

        String gradeName = optionalString(parameters, "gradeName", "grade_name");
        if (gradeName != null) {
            if (gradeName.length() > 100) {
                throw new BusinessException("gradeName 长度不能超过 100");
            }
            grade.setGradeName(gradeName);
        }

        grade.setUpdateTime(LocalDateTime.now());
        int row = gradeRecordMapper.updateById(grade);
        if (row <= 0) {
            throw new BusinessException("修改成绩失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gradeRecordId", grade.getId());
        data.put("studentId", grade.getStudentId());
        data.put("classId", grade.getClassId());
        data.put("courseId", grade.getCourseId());
        data.put("teacherId", grade.getTeacherId());
        data.put("oldScore", oldScore);
        data.put("newScore", grade.getScore());
        data.put("oldFullScore", oldFullScore);
        data.put("newFullScore", grade.getFullScore());
        data.put("oldGradeName", oldGradeName);
        data.put("newGradeName", grade.getGradeName());
        data.put("oldComment", oldComment);
        data.put("newComment", grade.getComment());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "成绩记录已修改",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以修改成绩");
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

    private BigDecimal requiredDecimal(Map<String, Object> parameters, String... keys) {
        BigDecimal value = optionalDecimal(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(keys[0] + " 不能小于 0");
        }
        return value;
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

    private void requirePositive(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(fieldName + " 必须大于 0");
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
