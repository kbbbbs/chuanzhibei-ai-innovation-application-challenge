package com.cupk.aiacademy.common;

/**
 * ThreadLocal holder for current logged-in user context
 * Used by AuthInterceptor to store user info per request
 */
public class UserContext {
    private static final ThreadLocal<AuthUtils.LoginUser> holder = new ThreadLocal<>();

    public static void set(AuthUtils.LoginUser user) {
        holder.set(user);
    }

    public static AuthUtils.LoginUser get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }

    public static Integer getCurrentUserId() {
        AuthUtils.LoginUser user = get();
        return user == null ? null : user.getUserId();
    }

    public static String getCurrentRoleCode() {
        AuthUtils.LoginUser user = get();
        return user == null ? null : user.getRoleCode();
    }
}
