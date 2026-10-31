package com.cupk.aiacademy.agent.action.teacher;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.agent.action.AgentActionExecutionContext;
import com.cupk.aiacademy.agent.action.AgentActionExecutionResult;
import com.cupk.aiacademy.agent.action.AgentActionExecutor;
import com.cupk.aiacademy.agent.action.AgentActionRiskLevel;
import com.cupk.aiacademy.agent.tool.teacher.TeacherApplicationReviewPlanTool;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
import com.cupk.aiacademy.teacherapply.TeacherApplication;
import com.cupk.aiacademy.teacherapply.TeacherApplicationMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TeacherApplicationReviewActionExecutor implements AgentActionExecutor {
    private static final String ACTION_CODE = "teacher.application.review";

    private final TeacherApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public TeacherApplicationReviewActionExecutor(TeacherApplicationMapper applicationMapper,
                                                  UserMapper userMapper,
                                                  RoleMapper roleMapper,
                                                  UserRoleMapper userRoleMapper) {
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String actionName() {
        return "审核教师申请";
    }

    @Override
    public AgentActionRiskLevel riskLevel() {
        return AgentActionRiskLevel.HIGH;
    }

    @Override
    public String confirmationText() {
        return TeacherApplicationReviewPlanTool.CONFIRMATION_TEXT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentActionExecutionResult execute(AgentActionExecutionContext context) {
        AuthUtils.LoginUser user = context.user();
        requireAdmin(user);

        Map<String, Object> parameters = context.parameters();
        TeacherApplication application = resolveApplication(parameters);
        if (application.getStatus() != null && application.getStatus() != 0) {
            throw new BusinessException("该申请已处理");
        }
        Integer status = requiredStatus(parameters);
        String reviewComment = optionalString(parameters, new String[]{"reviewComment", "review_comment", "comment"}, 255);

        application.setStatus(status);
        application.setReviewerId(user.getUserId());
        application.setReviewComment(reviewComment);
        application.setReviewTime(LocalDateTime.now());
        int row = applicationMapper.updateById(application);
        if (row <= 0) {
            throw new BusinessException("审核教师申请失败");
        }

        Integer teacherUserId = null;
        if (status == 1) {
            teacherUserId = createTeacherUser(application);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applicationId", application.getId());
        data.put("username", application.getUsername());
        data.put("status", status);
        data.put("statusLabel", status == 1 ? "通过" : "拒绝");
        data.put("reviewerId", user.getUserId());
        data.put("teacherUserId", teacherUserId);
        return new AgentActionExecutionResult(
                ACTION_CODE,
                actionName(),
                true,
                status == 1 ? "教师申请已通过" : "教师申请已拒绝",
                data,
                LocalDateTime.now()
        );
    }

    private TeacherApplication resolveApplication(Map<String, Object> parameters) {
        Integer applicationId = optionalInteger(parameters, "applicationId", "application_id", "id");
        if (applicationId != null) {
            TeacherApplication application = applicationMapper.selectById(applicationId);
            if (application == null) {
                throw new BusinessException("教师申请不存在");
            }
            return application;
        }
        String username = optionalString(parameters, new String[]{"username", "申请人", "账号"}, 100);
        if (username == null || username.isBlank()) {
            throw new BusinessException("applicationId 或 username 不能为空");
        }
        List<TeacherApplication> applications = applicationMapper.selectList(
                new QueryWrapper<TeacherApplication>().eq("username", username.trim()).eq("status", 0));
        if (applications == null || applications.isEmpty()) {
            throw new BusinessException("教师申请不存在：" + username);
        }
        if (applications.size() > 1) {
            throw new BusinessException("教师申请不唯一，请补充 applicationId：" + username);
        }
        return applications.get(0);
    }

    private Integer createTeacherUser(TeacherApplication application) {
        User existing = userMapper.selectOne(new QueryWrapper<User>().eq("username", application.getUsername()));
        if (existing != null) {
            throw new BusinessException("用户名已存在，无法通过申请");
        }
        Role teacherRole = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", "teacher"));
        if (teacherRole == null) {
            throw new BusinessException("教师角色未初始化");
        }

        LocalDateTime now = LocalDateTime.now();
        User teacher = new User();
        teacher.setUsername(application.getUsername());
        teacher.setPassword(application.getPassword());
        teacher.setNickname(application.getNickname());
        teacher.setEmail(application.getEmail());
        teacher.setPhone(application.getPhone());
        teacher.setStatus(1);
        teacher.setCreateTime(now);
        teacher.setUpdateTime(now);
        int userRow = userMapper.insert(teacher);
        if (userRow <= 0) {
            throw new BusinessException("创建教师用户失败");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(teacher.getId());
        userRole.setRoleId(teacherRole.getId());
        int roleRow = userRoleMapper.insert(userRole);
        if (roleRow <= 0) {
            throw new BusinessException("绑定教师角色失败");
        }
        return teacher.getId();
    }

    private void requireAdmin(AuthUtils.LoginUser user) {
        if (user == null || !"admin".equals(user.getRoleCode())) {
            throw new BusinessException("只有管理员可以审核教师申请");
        }
    }

    private Integer requiredStatus(Map<String, Object> parameters) {
        Integer status = optionalInteger(parameters, "status", "reviewStatus", "review_status");
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException("审核状态必须为通过或拒绝");
        }
        return status;
    }

    private String optionalString(Map<String, Object> parameters, String[] keys, int maxLength) {
        Object value = firstValue(parameters, keys);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (maxLength > 0 && text.length() > maxLength) {
            throw new BusinessException(keys[0] + " 长度不能超过 " + maxLength);
        }
        return text;
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
