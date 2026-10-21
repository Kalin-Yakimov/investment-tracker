package com.kalin.investment_tracker.auth;

import java.nio.charset.StandardCharsets;

/** One password policy for HTTP validation and service entry points; never trims credentials. */
public final class PasswordPolicy {
    public static final int MIN_NEW_PASSWORD_CHARACTERS = 10;
    public static final int MAX_PASSWORD_BYTES = 72;
    public static final String REQUIRED = "Password is required.";
    public static final String TOO_SHORT = "Password must be at least " + MIN_NEW_PASSWORD_CHARACTERS + " characters long.";
    public static final String TOO_LONG = "Password is too long. Use at most " + MAX_PASSWORD_BYTES
            + " UTF-8 bytes (non-English characters may use more than one byte).";
    public static final String INVALID_TEXT = "Password contains invalid Unicode text.";

    private PasswordPolicy() { }

    public static String validationError(String password, boolean newPassword) {
        if (password == null || password.isBlank()) return REQUIRED;
        // Bound work before allocating a byte buffer. UTF-8 cannot be shorter than UTF-16 length.
        if (password.length() > MAX_PASSWORD_BYTES) return TOO_LONG;
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(password)) return INVALID_TEXT;
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) return TOO_LONG;
        if (newPassword && password.codePointCount(0, password.length()) < MIN_NEW_PASSWORD_CHARACTERS) {
            return TOO_SHORT;
        }
        return null;
    }

    public static void requireValid(String password, boolean newPassword) {
        String error = validationError(password, newPassword);
        if (error != null) throw new IllegalArgumentException(error);
    }
}
