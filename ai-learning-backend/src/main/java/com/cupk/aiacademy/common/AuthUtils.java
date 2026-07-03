package com.cupk.aiacademy.common;

import io.jsonwebtoken.Claims;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {
    @Autowired
    private JwtUtils jwtUtils;

    public LoginUser requireLogin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("请先登录");
        }
        Claims claims = jwtUtils.parseToken(authorization.substring(7));
        Object userIdValue = claims.get("userId");
        if (!(userIdValue instanceof Number)) {
            throw new IllegalArgumentException("登录状态已失效");
        }

        LoginUser user = new LoginUser();
        user.setUserId(((Number) userIdValue).intValue());
        user.setUsername(claims.getSubject());
        Object roleCodeValue = claims.get("roleCode");
        user.setRoleCode(roleCodeValue == null ? "student" : roleCodeValue.toString());
        return user;
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

    @Data
    public static class LoginUser {
        private Integer userId;
        private String username;
        private String roleCode;
    }
}
