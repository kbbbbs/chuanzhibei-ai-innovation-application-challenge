package com.cupk.aiacademy.agent.action.notice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.agent.query.AgentWriteEntityLocator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NoticeUpdateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "notice.update";

    private final NoticeMapper noticeMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final AgentWriteEntityLocator entityLocator;

    public NoticeUpdateActionExecutor(NoticeMapper noticeMapper, TeachingClassMapper teachingClassMapper,
                                      AgentWriteEntityLocator entityLocator) {
        this.noticeMapper = noticeMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.entityLocator = entityLocator;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "修改通知";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Notice notice = resolveNotice(parameters);
        if (!isAdmin(user) && (notice.getPublisherId() == null || !notice.getPublisherId().equals(user.getUserId()))) {
            throw new BusinessException("只能修改当前登录用户发布的通知");
        }

        Map<String, Object> oldValues = snapshot(notice);
        boolean changed = applyUpdates(notice, parameters);
        if (!changed) {
            throw new BusinessException("至少需要提供一个可修改的通知字段");
        }

        int row = noticeMapper.updateById(notice);
        if (row <= 0) {
            throw new BusinessException("修改通知失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeId", notice.getId());
        data.put("publisherId", notice.getPublisherId());
        data.put("oldTitle", oldValues.get("title"));
        data.put("newTitle", notice.getTitle());
        data.put("oldContent", oldValues.get("content"));
        data.put("newContent", notice.getContent());
        data.put("oldNoticeType", oldValues.get("noticeType"));
        data.put("newNoticeType", notice.getNoticeType());
        data.put("oldClassId", oldValues.get("classId"));
        data.put("newClassId", notice.getClassId());
        data.put("status", notice.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "通知已修改",
                data,
                LocalDateTime.now()
        );
    }

    private Notice resolveNotice(Map<String, Object> parameters) {
        Integer noticeId = optionalInteger(parameters, "noticeId", "notice_id", "id");
        String titleLocator = optionalString(parameters,
                "originalTitle", "original_title", "oldTitle", "old_title", "currentTitle", "current_title");
        return entityLocator.locateNotice(noticeId, titleLocator);
    }

    private boolean applyUpdates(Notice notice, Map<String, Object> parameters) {
        boolean changed = false;
        if (hasAnyKey(parameters, "title")) {
            notice.setTitle(requiredString(parameters, "title", 100));
            changed = true;
        }
        if (hasAnyKey(parameters, "content")) {
            notice.setContent(optionalString(parameters, "content"));
            changed = true;
        }
        if (hasAnyKey(parameters, "noticeType", "notice_type")) {
            notice.setNoticeType(optionalString(parameters, "noticeType", "notice_type"));
            changed = true;
        }
        if (hasAnyKey(parameters, "classId", "class_id")) {
            Integer classId = optionalInteger(parameters, "classId", "class_id");
            if (classId != null && teachingClassMapper.selectById(classId) == null) {
                throw new BusinessException("教学班不存在");
            }
            notice.setClassId(classId);
            changed = true;
        }
        return changed;
    }

    private Map<String, Object> snapshot(Notice notice) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", notice.getTitle());
        values.put("content", notice.getContent());
        values.put("noticeType", notice.getNoticeType());
        values.put("classId", notice.getClassId());
 return values;
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以修改通知");
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
