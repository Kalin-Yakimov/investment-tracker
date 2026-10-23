package com.kalin.investment_tracker.handler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

public enum BusinessErrorCodes {

    NO_CODE(0, NOT_IMPLEMENTED, "No code"),

    INCORRECT_CURRENT_PASSWORD(300, BAD_REQUEST, "Current password is incorrect"),

    NEW_PASSWORD_DOES_NOT_MATCH(301, BAD_REQUEST, "New passwords do not match"),

    ACCOUNT_LOCKED(302, FORBIDDEN, "User account is locked"),

    ACCOUNT_DISABLED(303, FORBIDDEN, "User account is disabled"),

    BAD_CREDENTIALS(304, FORBIDDEN, "Login and / or password is incorrect"),

    USERNAME_ALREADY_TAKEN(305, CONFLICT, "Username is already taken by another account"),

    EXPIRED_TOKEN(401, UNAUTHORIZED, "Expired Token")
    ;

    @Getter
    private final int code;
    @Getter
    private final String description;
    @Getter
    private final HttpStatus httpStatus;

    BusinessErrorCodes(int code, HttpStatus httpStatus, String description ) {
        this.code = code;
        this.description = description;
        this.httpStatus = httpStatus;
    }
}
