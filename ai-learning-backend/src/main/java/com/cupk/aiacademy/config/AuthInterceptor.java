package com.cupk.aiacademy.config;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
public class AuthInterceptor implements AsyncHandlerInterceptor {

    @Autowired(required = false)
    private AuthUtils authUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        if (request.getRequestURI().startsWith("/api/setup/")
                || request.getRequestURI().startsWith("/api/admin/settings/")) {
            // Installation endpoints enforce their own session or live administrator authorization.
            return true;
        }
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

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE leaves the servlet thread before afterCompletion; never leak its user into the next request.
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
        if (path.startsWith("/media")) {
            return false;
        }
        if ("/api/home-course-recommendations".equals(path)
                && "GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        return true;
    }
}
