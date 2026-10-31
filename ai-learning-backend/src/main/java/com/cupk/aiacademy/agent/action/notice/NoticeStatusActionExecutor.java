package com.cupk.aiacademy.agent.action.notice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.notice.NoticeStatusPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.NoticeMapper;
import com.cupk.aiacademy.pojo.Notice;
import com.cupk.aiacademy.agent.query.AgentWriteEntityLocator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NoticeStatusActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "notice.status";

    private final NoticeMapper noticeMapper;
    private final AgentWriteEntityLocator entityLocator;

    public NoticeStatusActionExecutor(NoticeMapper noticeMapper, AgentWriteEntityLocator entityLocator) {
        this.noticeMapper = noticeMapper;
        this.entityLocator = entityLocator;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "变更通知状态";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return NoticeStatusPlanTool.CONFIRMATION_TEXT;
    }

    @Override
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireTeacherOrAdmin(user);

        Map<String, Object> parameters = context.parameters();
        Integer status = requiredInteger(parameters, "status");
        requireValidStatus(status);

        Notice notice = resolveNotice(parameters);
        if (!isAdmin(user) && (notice.getPublisherId() == null || !notice.getPublisherId().equals(user.getUserId()))) {
            throw new BusinessException("只能变更当前登录用户发布通知的状态");
        }

        Integer oldStatus = notice.getStatus();
        notice.setStatus(status);
        int row = noticeMapper.updateById(notice);
        if (row <= 0) {
            throw new BusinessException("变更通知状态失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeId", notice.getId());
        data.put("publisherId", notice.getPublisherId());
        data.put("title", notice.getTitle());
        data.put("oldStatus", oldStatus);
        data.put("newStatus", notice.getStatus());
        data.put("reason", optionalString(parameters, "reason"));
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                "通知状态已变更",
                data,
                LocalDateTime.now()
        );
    }

    private Notice resolveNotice(Map<String, Object> parameters) {
        Integer noticeId = optionalInteger(parameters, "noticeId", "notice_id", "id");
        String title = optionalString(parameters, "title");
        return entityLocator.locateNotice(noticeId, title);
    }

    private void requireTeacherOrAdmin(AuthUtils.LoginUser user) {
        if (user == null || (!isTeacher(user) && !isAdmin(user))) {
            throw new BusinessException("只有教师或管理员可以变更通知状态");
        }
    }

    private boolean isTeacher(AuthUtils.LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    private boolean isAdmin(AuthUtils.LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    private void requireValidStatus(Integer status) {
        if (status == null || status < 0 || status > 1) {
            throw new BusinessException("通知状态只能是 0 或 1");
        }
    }

    private Integer requiredInteger(Map<String, Object> parameters, String... keys) {
        Integer value = optionalInteger(parameters, keys);
        if (value == null) {
            throw new BusinessException(keys[0] + " 不能为空");
        }
        return value;
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
