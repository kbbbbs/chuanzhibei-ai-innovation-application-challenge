package com.cupk.aiacademy.agent.action.homework;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Homework;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HomeworkCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "homework.create";

    private final HomeworkMapper homeworkMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final UserMapper userMapper;

    public HomeworkCreateActionExecutor(HomeworkMapper homeworkMapper,
                                        TeachingClassMapper teachingClassMapper,
                                        UserMapper userMapper) {
        this.homeworkMapper = homeworkMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "发布作业";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer classId = requiredInteger(parameters, "classId", "class_id");
        if (teachingClassMapper.selectById(classId) == null) {
            throw new BusinessException("教学班不存在");
        }

        Integer teacherId = optionalInteger(parameters, "teacherId", "teacher_id");
        if (teacherId == null) {
            teacherId = user.getUserId();
        }
        if (!teacherId.equals(user.getUserId()) && !isAdmin(user)) {
            throw new BusinessException("只能以当前登录教师身份发布作业");
        }
        if (userMapper.selectById(teacherId) == null) {
            throw new BusinessException("教师用户不存在");
        }

        String title = requiredString(parameters, "title", 100);
        BigDecimal fullScore = optionalDecimal(parameters, "fullScore", "full_score");
        if (fullScore == null) {
            fullScore = BigDecimal.valueOf(100);
        }
        if (fullScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("作业满分必须大于 0");
        }

        LocalDateTime startTime = optionalDateTime(parameters, "startTime", "start_time");
        LocalDateTime deadline = optionalDateTime(parameters, "deadline", "endTime", "end_time");
        if (startTime != null && deadline != null && !deadline.isAfter(startTime)) {
            throw new BusinessException("截止时间必须晚于开始时间");
        }

        Integer status = optionalInteger(parameters, "status");
        if (status == null) {
            status = 1;
        }
        if (status < 0) {
            throw new BusinessException("作业状态不能小于 0");
        }

        LocalDateTime now = LocalDateTime.now();
        Homework homework = new Homework();
        homework.setClassId(classId);
        homework.setTeacherId(teacherId);
        homework.setTitle(title);
        homework.setContent(optionalString(parameters, "content"));
        homework.setAttachmentUrl(optionalString(parameters, "attachmentUrl", "attachment_url"));
        homework.setFullScore(fullScore);
        homework.setStartTime(startTime);
        homework.setDeadline(deadline);
        homework.setStatus(status);
        homework.setCreateTime(now);
        homework.setUpdateTime(now);

        int row = homeworkMapper.insert(homework);
        if (row <= 0) {
            throw new BusinessException("发布作业失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("homeworkId", homework.getId());
        data.put("classId", homework.getClassId());
        data.put("teacherId", homework.getTeacherId());
        data.put("title", homework.getTitle());
        data.put("fullScore", homework.getFullScore());
        data.put("status", homework.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "作业已发布",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以发布作业");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private String requiredString(Map<String, Object> parameters, String key, int maxLength) {
        String value = optionalString(parameters, key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(key + " 不能为空");
        }
        value = value.trim();
        if (maxLength > 0 && value.length() > maxLength) {
            throw new BusinessException(key + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
    }

    private String optionalString(Map<String, Object> parameters, String... keys) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private LocalDateTime optionalDateTime(Map<String, Object> parameters, String... keys) {
        String value = optionalString(parameters, keys);
        if (value == null) {
            return null;
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
