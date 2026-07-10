package com.cupk.aiacademy.config;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired(required = false)
    private AuthUtils authUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");

        if (authUtils == null) {
            return true;
        }

        if (needsAuth(request)) {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                response.setStatus(401);
                return false;
            }
        }

        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                AuthUtils.LoginUser user = authUtils.requireLogin(authorization);
                UserContext.set(user);
            } catch (Exception e) {
                response.setStatus(401);
                return false;
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean needsAuth(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path.matches(".*/auth/(login|register).*")) {
            return false;
        }
        if ("/teacher-applications".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if (path.startsWith("/uploads")) {
            return false;
        }
        if (path.startsWith("/api/weknora/local-file/")) {
            return false;
        }
        if (path.startsWith("/media")) {
            return false;
        }

        return true;
    }
}
