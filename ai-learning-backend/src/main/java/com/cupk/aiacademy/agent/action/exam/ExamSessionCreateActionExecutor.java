package com.cupk.aiacademy.agent.action.exam;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.exam.ExamSessionCreatePlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.dto.ExamRequestDTO;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.service.ExamService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExamSessionCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "exam.session.create";

    private final ExamService examService;
    private final TeachingClassMapper teachingClassMapper;
    private final UserMapper userMapper;

    public ExamSessionCreateActionExecutor(ExamService examService,
                                           TeachingClassMapper teachingClassMapper,
                                           UserMapper userMapper) {
        this.examService = examService;
        this.teachingClassMapper = teachingClassMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "创建考试安排";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return ExamSessionCreatePlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Long classId = requiredLong(parameters, "classId", "class_id");
        if (teachingClassMapper.selectById(classId.intValue()) == null) {
            throw new BusinessException("教学班不存在");
        }

        Long teacherId = optionalLong(parameters, "teacherId", "teacher_id");
        if (teacherId == null) {
            teacherId = user.getUserId().longValue();
        }
        if (!teacherId.equals(user.getUserId().longValue()) && !isAdmin(user)) {
            throw new BusinessException("只能以当前登录教师身份创建考试");
        }
        if (userMapper.selectById(teacherId.intValue()) == null) {
            throw new BusinessException("教师用户不存在");
        }

        ExamRequestDTO request = new ExamRequestDTO();
        request.setClassId(classId);
        request.setTeacherId(teacherId);
        request.setPaperId(requiredLong(parameters, "paperId", "paper_id"));
        request.setName(requiredString(parameters, "name", "title"));
        request.setDescription(optionalString(parameters, "description"));
        request.setStartTime(requiredDateTime(parameters, "startTime", "start_time"));
        request.setEndTime(requiredDateTime(parameters, "endTime", "end_time"));
        request.setDurationMinutes(requiredInteger(parameters, "durationMinutes", "duration", "duration_minutes"));
        request.setStatus(optionalExamStatus(parameters, "status"));

        ExamResponseDTO response = examService.create(request);

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
                "考试安排已创建",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以创建考试");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private String requiredString(Map<String, Object> parameters, String... keys) {
        String value = optionalString(parameters, keys);
        if (value == null || value.isBlank()) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value.trim();
    }

    private String optionalString(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private Integer optionalInteger(Map<String, Object> parameters, String... keys) {
        Long value = optionalLong(parameters, keys);
        if (value == null) {
            return null;
        }
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new BusinessException(keys[0] + " 不合法");
        }
        return value.intValue();
    }

    private Long requiredLong(Map<String, Object> parameters, String... keys) {
        Long value = optionalLong(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new BusinessException(keys[0] + " 不合法");
        }
        return value;
    }

    private Long optionalLong(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(keys[0] + " 必须是整数");
        }
    }

    private LocalDateTime requiredDateTime(Map<String, Object> parameters, String... keys) {
        String value = optionalString(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return parseDateTime(value, keys[0]);
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            // Try common model output formats below.
        }
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (RuntimeException ignored) {
                // Continue trying compatible formats.
            }
        }
        throw new BusinessException(fieldName + " 必须是日期时间格式，例如 2026-07-05 18:00:00");
    }

    private ExamStatus optionalExamStatus(Map<String, Object> parameters, String key) {
        Object value = firstValue(parameters, key);
        if (value == null || String.valueOf(value).isBlank()) {
            return ExamStatus.DRAFT;
        }
        if (value instanceof Number number) {
            return ExamStatus.fromCode(number.intValue());
        }
        String text = String.valueOf(value).trim();
        if ("published".equalsIgnoreCase(text) || "publish".equalsIgnoreCase(text) || "发布".equals(text)) {
            return ExamStatus.PUBLISHED;
        }
        if ("closed".equalsIgnoreCase(text) || "close".equalsIgnoreCase(text) || "关闭".equals(text)) {
            return ExamStatus.CLOSED;
        }
        if ("draft".equalsIgnoreCase(text) || "草稿".equals(text)) {
            return ExamStatus.DRAFT;
        }
        try {
            return ExamStatus.fromCode(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            throw new BusinessException("status 必须是 DRAFT、PUBLISHED、CLOSED 或状态码");
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
