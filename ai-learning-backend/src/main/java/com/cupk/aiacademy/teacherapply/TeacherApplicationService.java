package com.cupk.aiacademy.teacherapply;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.PasswordUtils;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
import com.cupk.aiacademy.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TeacherApplicationService {
    private final TeacherApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;

    public TeacherApplicationService(TeacherApplicationMapper applicationMapper, UserMapper userMapper,
                                     RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                                     PermissionService permissionService) {
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public TeacherApplication apply(TeacherApplicationRequest request) {
        if (userMapper.selectOne(new QueryWrapper<User>().eq("username", request.getUsername().trim())) != null) {
            throw new BusinessException("用户名已存在，请直接登录或更换用户名");
        }
        Long pendingCount = applicationMapper.selectCount(new QueryWrapper<TeacherApplication>()
                .eq("username", request.getUsername().trim())
                .eq("status", 0));
        if (pendingCount > 0) {
            throw new BusinessException("该用户名已有待审核申请，请等待管理员处理");
        }
        TeacherApplication app = new TeacherApplication();
        app.setUsername(request.getUsername().trim());
        app.setPassword(PasswordUtils.encode(request.getPassword()));
        app.setNickname(blankTo(request.getNickname(), request.getUsername()).trim());
        app.setEmail(request.getEmail());
        app.setPhone(request.getPhone());
        app.setReason(request.getReason().trim());
        app.setStatus(0);
        app.setCreateTime(LocalDateTime.now());
        applicationMapper.insert(app);
        app.setPassword(null);
        return app;
    }

    public Page<TeacherApplication> list(Integer status, int pageNum, int pageSize) {
        return list(status, null, pageNum, pageSize);
    }

    public Page<TeacherApplication> list(Integer status, String keyword, int pageNum, int pageSize) {
        permissionService.requireAdmin();
        Page<TeacherApplication> page = new Page<>(Math.max(pageNum, 1), Math.max(pageSize, 1));
        QueryWrapper<TeacherApplication> query = new QueryWrapper<>();
        if (status != null) query.eq("status", status);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String term = keyword.trim();
            query.and(wrapper -> wrapper.like("username", term)
                    .or()
                    .like("nickname", term)
                    .or()
                    .like("email", term)
                    .or()
                    .like("phone", term)
                    .or()
                    .like("reason", term));
        }
        query.orderByDesc("id");
        applicationMapper.selectPage(page, query);
        page.getRecords().forEach(item -> item.setPassword(null));
        return page;
    }

    @Transactional
    public TeacherApplication review(Integer id, TeacherApplicationReviewRequest request) {
        permissionService.requireAdmin();
        TeacherApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("教师申请不存在");
        if (app.getStatus() != null && app.getStatus() != 0) throw new BusinessException("该申请已处理");
        if (request.getStatus() == null || (request.getStatus() != 1 && request.getStatus() != 2)) {
            throw new BusinessException("审核状态必须为通过或拒绝");
        }
        app.setStatus(request.getStatus());
        app.setReviewerId(permissionService.currentUser().getUserId());
        app.setReviewComment(request.getReviewComment());
        app.setReviewTime(LocalDateTime.now());
        applicationMapper.updateById(app);
        if (request.getStatus() == 1) createTeacherUser(app);
        app.setPassword(null);
        return app;
    }

    private void createTeacherUser(TeacherApplication app) {
        if (userMapper.selectOne(new QueryWrapper<User>().eq("username", app.getUsername())) != null) {
            throw new BusinessException("用户名已存在，无法通过申请");
        }
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", "teacher"));
        if (role == null) throw new BusinessException("教师角色未初始化");
        User user = new User();
        user.setUsername(app.getUsername());
        user.setPassword(app.getPassword());
        user.setNickname(app.getNickname());
        user.setEmail(app.getEmail());
        user.setPhone(app.getPhone());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
