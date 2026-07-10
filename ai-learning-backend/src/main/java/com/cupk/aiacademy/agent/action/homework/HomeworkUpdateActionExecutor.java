package com.cupk.aiacademy.agent.action.homework;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.HomeworkMapper;
import com.cupk.aiacademy.pojo.Homework;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HomeworkUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "homework.update";

    private final HomeworkMapper homeworkMapper;

    public HomeworkUpdateActionExecutor(HomeworkMapper homeworkMapper) {
        this.homeworkMapper = homeworkMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "修改作业";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer homeworkId = requiredInteger(parameters, "homeworkId", "homework_id", "assignmentId", "id");
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }
        if (!isAdmin(user) && (homework.getTeacherId() == null || !homework.getTeacherId().equals(user.getUserId()))) {
            throw new BusinessException("只能修改当前登录教师发布的作业");
        }

        Map<String, Object> oldValues = snapshot(homework);
        boolean changed = applyUpdates(homework, parameters);
        if (!changed) {
            throw new BusinessException("至少需要提供一个可修改的作业字段");
        }
        if (homework.getStartTime() != null
                && homework.getDeadline() != null
                && !homework.getDeadline().isAfter(homework.getStartTime())) {
            throw new BusinessException("截止时间必须晚于开始时间");
        }

        homework.setUpdateTime(LocalDateTime.now());
        int row = homeworkMapper.updateById(homework);
        if (row <= 0) {
            throw new BusinessException("修改作业失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("homeworkId", homework.getId());
        data.put("classId", homework.getClassId());
        data.put("teacherId", homework.getTeacherId());
        data.put("oldTitle", oldValues.get("title"));
        data.put("newTitle", homework.getTitle());
        data.put("oldContent", oldValues.get("content"));
        data.put("newContent", homework.getContent());
        data.put("oldAttachmentUrl", oldValues.get("attachmentUrl"));
        data.put("newAttachmentUrl", homework.getAttachmentUrl());
        data.put("oldFullScore", oldValues.get("fullScore"));
        data.put("newFullScore", homework.getFullScore());
        data.put("oldStartTime", formatDateTime((LocalDateTime) oldValues.get("startTime")));
        data.put("newStartTime", formatDateTime(homework.getStartTime()));
        data.put("oldDeadline", formatDateTime((LocalDateTime) oldValues.get("deadline")));
        data.put("newDeadline", formatDateTime(homework.getDeadline()));
        data.put("oldStatus", oldValues.get("status"));
        data.put("newStatus", homework.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "作业已修改",
                data,
                LocalDateTime.now()
        );
    }

    private boolean applyUpdates(Homework homework, Map<String, Object> parameters) {
        boolean changed = false;
        if (hasAnyKey(parameters, "title")) {
            homework.setTitle(requiredString(parameters, "title", 100));
            changed = true;
        }
        if (hasAnyKey(parameters, "content")) {
            homework.setContent(optionalString(parameters, "content"));
            changed = true;
        }
        if (hasAnyKey(parameters, "attachmentUrl", "attachment_url")) {
            String attachmentUrl = optionalString(parameters, "attachmentUrl", "attachment_url");
            if (attachmentUrl != null && attachmentUrl.length() > 255) {
                throw new BusinessException("attachmentUrl 长度不能超过 255");
            }
            homework.setAttachmentUrl(attachmentUrl);
            changed = true;
        }
        if (hasAnyKey(parameters, "fullScore", "full_score")) {
            BigDecimal fullScore = requiredDecimal(parameters, "fullScore", "full_score");
            if (fullScore.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("作业满分必须大于 0");
            }
            homework.setFullScore(fullScore);
            changed = true;
        }
        if (hasAnyKey(parameters, "startTime", "start_time")) {
            homework.setStartTime(optionalDateTime(parameters, "startTime", "start_time"));
            changed = true;
        }
        if (hasAnyKey(parameters, "deadline", "endTime", "end_time")) {
            homework.setDeadline(optionalDateTime(parameters, "deadline", "endTime", "end_time"));
            changed = true;
        }
        if (hasAnyKey(parameters, "status")) {
            Integer status = requiredInteger(parameters, "status");
            if (status < 0 || status > 2) {
                throw new BusinessException("作业状态只能是 0、1 或 2");
            }
            homework.setStatus(status);
            changed = true;
        }
        return changed;
    }

    private Map<String, Object> snapshot(Homework homework) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", homework.getTitle());
        values.put("content", homework.getContent());
        values.put("attachmentUrl", homework.getAttachmentUrl());
        values.put("fullScore", homework.getFullScore());
        values.put("startTime", homework.getStartTime());
        values.put("deadline", homework.getDeadline());
        values.put("status", homework.getStatus());
        return values;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以修改作业");
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

    private BigDecimal requiredDecimal(Map<String, Object> parameters, String... keys) {
        BigDecimal value = optionalDecimal(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
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
