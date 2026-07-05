package com.cupk.aiacademy.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtils {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String MD5_PATTERN = "^[a-fA-F0-9]{32}$";

    private PasswordUtils() {
    }

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return ENCODER.matches(rawPassword, storedPassword);
        }
        return MD5Utils.md5(rawPassword).equalsIgnoreCase(storedPassword);
    }

    public static boolean needsUpgrade(String storedPassword) {
        return storedPassword != null && storedPassword.matches(MD5_PATTERN);
    }

    private static boolean isBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
