package com.cupk.aiacademy.agent.action.homework;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.homework.HomeworkSubmissionGradePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.HomeworkSubmitMapper;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.HomeworkSubmit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HomeworkSubmissionGradeActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "homework.submission.grade";

    private final HomeworkSubmitMapper homeworkSubmitMapper;
    private final HomeworkMapper homeworkMapper;

    public HomeworkSubmissionGradeActionExecutor(HomeworkSubmitMapper homeworkSubmitMapper,
                                                 HomeworkMapper homeworkMapper) {
        this.homeworkSubmitMapper = homeworkSubmitMapper;
        this.homeworkMapper = homeworkMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "批改作业提交";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return HomeworkSubmissionGradePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer homeworkSubmitId = requiredInteger(parameters, "homeworkSubmitId", "homework_submit_id",
                "submitId", "submissionId", "id");
        HomeworkSubmit submit = homeworkSubmitMapper.selectById(homeworkSubmitId);
        if (submit == null) {
            throw new BusinessException("作业提交不存在");
        }
        Homework homework = homeworkMapper.selectById(submit.getHomeworkId());
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }
        if (!isAdmin(user) && (homework.getTeacherId() == null || !homework.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能批改当前登录教师发布作业的提交");
        }

        BigDecimal oldScore = submit.getScore();
        String oldFeedback = submit.getFeedback();
        Integer oldGradeStatus = submit.getGradeStatus();

        BigDecimal score = requiredDecimal(parameters, "score");
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("score 不能小于 0");
        }
        if (homework.getFullScore() != null && score.compareTo(homework.getFullScore()) > 0) {
            throw new BusinessException("分数不能超过作业满分");
        }
        String feedback = optionalString(parameters, "feedback", "comment", "gradeComment", "grade_comment");

        LocalDateTime now = LocalDateTime.now();
        submit.setScore(score);
        submit.setFeedback(feedback);
        submit.setGradeStatus(1);
        submit.setGraderId(user.getUserId());
        submit.setGradeTime(now);
        submit.setUpdateTime(now);
        int row = homeworkSubmitMapper.updateById(submit);
        if (row <= 0) {
            throw new BusinessException("批改作业失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("homeworkSubmitId", submit.getId());
        data.put("homeworkId", submit.getHomeworkId());
        data.put("studentId", submit.getStudentId());
        data.put("graderId", submit.getGraderId());
        data.put("oldScore", oldScore);
        data.put("newScore", submit.getScore());
        data.put("fullScore", homework.getFullScore());
        data.put("oldFeedback", oldFeedback);
        data.put("newFeedback", submit.getFeedback());
        data.put("oldGradeStatus", oldGradeStatus);
        data.put("newGradeStatus", submit.getGradeStatus());
        data.put("gradeTime", formatDateTime(submit.getGradeTime()));
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "作业提交已批改",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以批改作业");
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
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
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

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
