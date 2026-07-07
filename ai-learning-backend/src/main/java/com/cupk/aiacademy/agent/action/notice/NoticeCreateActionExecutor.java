package com.cupk.aiacademy.agent.action.notice;

import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.pojo.Notice;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NoticeCreateActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "notice.create";

    private final NoticeMapper noticeMapper;
    private final UserMapper userMapper;
    private final TeachingClassMapper teachingClassMapper;

    public NoticeCreateActionExecutor(NoticeMapper noticeMapper,
                                      UserMapper userMapper,
                                      TeachingClassMapper teachingClassMapper) {
        this.noticeMapper = noticeMapper;
        this.userMapper = userMapper;
        this.teachingClassMapper = teachingClassMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "发布通知";
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        String title = requiredString(parameters, "title", 100);
        String content = requiredString(parameters, "content", 0);
        Integer publisherId = optionalInteger(parameters, "publisherId", "publisher_id");
        if (publisherId == null) {
            publisherId = user.getUserId();
        }
        if (!publisherId.equals(user.getUserId()) && !isAdmin(user)) {
            throw new BusinessException("只能以当前登录用户身份发布通知");
        }
        if (userMapper.selectById(publisherId) == null) {
            throw new BusinessException("发布用户不存在");
        }

        Integer classId = optionalInteger(parameters, "classId", "class_id");
        if (classId != null && teachingClassMapper.selectById(classId) == null) {
            throw new BusinessException("教学班不存在");
        }

        Integer status = optionalInteger(parameters, "status");
        if (status == null) {
            status = 1;
        }
        if (status < 0) {
            throw new BusinessException("通知状态不能小于 0");
        }

        Notice notice = new Notice();
        notice.setPublisherId(publisherId);
        notice.setClassId(classId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setNoticeType(optionalString(parameters, "noticeType", "notice_type"));
        notice.setStatus(status);
        notice.setCreateTime(LocalDateTime.now());

        int row = noticeMapper.insert(notice);
        if (row <= 0) {
            throw new BusinessException("发布通知失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeId", notice.getId());
        data.put("publisherId", notice.getPublisherId());
        data.put("classId", notice.getClassId());
        data.put("title", notice.getTitle());
        data.put("status", notice.getStatus());
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "通知已发布",
                data,
                LocalDateTime.now()
        );
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以发布通知");
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
}
