package com.kalin.investment_tracker.auth;

public class InvalidVerificationCodeException extends IllegalArgumentException {
    public InvalidVerificationCodeException() {
        super("Invalid or expired code. Check your email and code, or request a new code.");
    }
}
