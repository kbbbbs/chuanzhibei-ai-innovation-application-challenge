package com.cupk.aiacademy.config;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Global authentication interceptor
 * Extracts JWT token from Authorization header and stores user context
 */
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

        // Check if authentication is required for this path
        if (needsAuth(request)) {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                response.setStatus(401);
                return false;
            }
        }

        // If Authorization header is present, parse and store user context
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
        // Clear user context after request completes
        UserContext.clear();
    }

    /**
     * Determine if the request path requires authentication
     */
    private boolean needsAuth(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Public endpoints that don't require authentication
        if (path.matches(".*/auth/(login|register).*")) {
            return false;
        }
        if (path.startsWith("/uploads")) {
            return false;
        }

        // All other endpoints require authentication
        return true;
    }
}
