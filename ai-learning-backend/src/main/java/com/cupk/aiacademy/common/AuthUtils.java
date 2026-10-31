package com.cupk.aiacademy.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.mapper.RoleMapper;
import com.cupk.aiacademy.mapper.UserMapper;
import com.cupk.aiacademy.mapper.UserRoleMapper;
import com.cupk.aiacademy.pojo.Role;
import com.cupk.aiacademy.pojo.User;
import com.cupk.aiacademy.pojo.UserRole;
import io.jsonwebtoken.Claims;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    public LoginUser requireLogin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("请先登录");
        }
        Claims claims = jwtUtils.parseToken(authorization.substring(7));
        Object userIdValue = claims.get("userId");
        if (!(userIdValue instanceof Number)) {
            throw new IllegalArgumentException("登录状态已失效");
        }

        Integer userId = ((Number) userIdValue).intValue();
        User dbUser = userMapper.selectById(userId);
        if (dbUser == null || dbUser.getStatus() == null || dbUser.getStatus() == 0) {
            throw new IllegalArgumentException("登录状态已失效");
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUsername(dbUser.getUsername() == null ? claims.getSubject() : dbUser.getUsername());
        loginUser.setRoleCode(findRoleCode(userId));
        return loginUser;
    }

    public boolean isAdmin(LoginUser user) {
        return user != null && "admin".equals(user.getRoleCode());
    }

    public boolean isTeacher(LoginUser user) {
        return user != null && "teacher".equals(user.getRoleCode());
    }

    public boolean isStudent(LoginUser user) {
        return user != null && "student".equals(user.getRoleCode());
    }

    public String findRoleCode(Integer userId) {
        String assigned = findAssignedRoleCode(userId);
        return assigned == null ? "student" : assigned;
    }

    /** Strict lookup for authorization that must distinguish an assignment from the legacy default. */
    public String findAssignedRoleCode(Integer userId) {
        if (userId == null) {
            return null;
        }
        UserRole userRole = userRoleMapper.selectOne(new QueryWrapper<UserRole>().eq("user_id", userId));
        if (userRole == null || userRole.getRoleId() == null) {
            return null;
        }
        Role role = roleMapper.selectById(userRole.getRoleId());
        return role == null ? null : role.getRoleCode();
    }

    @Data
    public static class LoginUser {
        private Integer userId;
        private String username;
        private String roleCode;
    }
}
