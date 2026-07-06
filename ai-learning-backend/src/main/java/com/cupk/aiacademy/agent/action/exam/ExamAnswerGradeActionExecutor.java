package com.cupk.aiacademy.agent.action.exam;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.exam.ExamAnswerGradePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.SubmissionRequestsDTO;
import com.cupk.aiacademy.dto.SubmissionResponseDTO;
import com.cupk.aiacademy.mapper.ExamSessionMapper;
import com.cupk.aiacademy.pojo.ExamRecord;
import com.cupk.aiacademy.pojo.ExamSession;
import com.cupk.aiacademy.service.SubmissionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExamAnswerGradeActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.answer.grade";

    private final SubmissionService submissionService;
    private final ExamSessionMapper examSessionMapper;

    public ExamAnswerGradeActionExecutor(SubmissionService submissionService,
                                         ExamSessionMapper examSessionMapper) {
        this.submissionService = submissionService;
        this.examSessionMapper = examSessionMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "批改考试答题";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return ExamAnswerGradePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Long submissionId = requiredLong(parameters, "submissionId", "submission_id", "examRecordId", "exam_record_id");
        ExamRecord record = submissionService.getEntityById(submissionId);
        ExamSession exam = examSessionMapper.selectById(record.getExamSessionId());
        if (exam == null) {
            throw new BusinessException("考试安排不存在");
        }
        if (!isAdmin(user) && (exam.getTeacherId() == null || !exam.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能批改当前登录教师负责的考试答卷");
        }

        SubmissionRequestsDTO.ManualGradeRequest request = new SubmissionRequestsDTO.ManualGradeRequest();
        request.setGraderId(user.getUserId().longValue());
        request.setGrades(parseGrades(parameters));

        SubmissionResponseDTO response = submissionService.manualGrade(submissionId, request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("submissionId", response.getId());
        data.put("examId", response.getExamId());
        data.put("studentId", response.getStudentId());
        data.put("objectiveScore", response.getObjectiveScore());
        data.put("subjectiveScore", response.getSubjectiveScore());
        data.put("totalScore", response.getTotalScore());
        data.put("status", response.getStatus() == null ? null : response.getStatus().name());
        data.put("gradedCount", request.getGrades().size());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "考试答题已批改",
                data,
                LocalDateTime.now()
        );
    }

    private List<SubmissionRequestsDTO.ManualGradeItem> parseGrades(Map<String, Object> parameters) {
        Object gradesValue = firstValue(parameters, "grades");
        if (gradesValue instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new BusinessException("grades 不能为空");
            }
            List<SubmissionRequestsDTO.ManualGradeItem> grades = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new BusinessException("grades 中每一项必须是对象");
                }
                grades.add(toGradeItem(map));
            }
            return grades;
        }
        return List.of(toGradeItem(parameters));
    }

    private SubmissionRequestsDTO.ManualGradeItem toGradeItem(Map<?, ?> parameters) {
        Long submissionAnswerId = requiredLong(parameters, "submissionAnswerId", "submission_answer_id", "answerId", "id");
        BigDecimal score = requiredDecimal(parameters, "score");
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("score 不能小于 0");
        }
        String comment = optionalString(parameters, "comment", "gradeComment", "grade_comment");
        if (comment != null && comment.length() > 255) {
            throw new BusinessException("comment 长度不能超过 255");
        }

        SubmissionRequestsDTO.ManualGradeItem item = new SubmissionRequestsDTO.ManualGradeItem();
        item.setSubmissionAnswerId(submissionAnswerId);
        item.setScore(score);
        item.setComment(comment);
        return item;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以批改考试");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private Long requiredLong(Map<?, ?> parameters, String... keys) {
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

    private BigDecimal requiredDecimal(Map<?, ?> parameters, String... keys) {
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

    private String optionalString(Map<?, ?> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Object firstValue(Map<?, ?> parameters, String... keys) {
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
