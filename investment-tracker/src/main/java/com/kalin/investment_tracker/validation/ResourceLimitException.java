package com.kalin.investment_tracker.validation;

import org.springframework.http.HttpStatus;

public final class ResourceLimitException extends RuntimeException {
    private final HttpStatus status;
    private final long retryAfterSeconds;

    public ResourceLimitException(HttpStatus status, String message, long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }
    public HttpStatus getStatus() { return status; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
