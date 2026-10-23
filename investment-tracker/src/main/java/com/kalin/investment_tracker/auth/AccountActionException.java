package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.handler.BusinessErrorCodes;

/** Expected account-operation failure with a fixed public message, never credentials. */
public final class AccountActionException extends RuntimeException {
    private final BusinessErrorCodes errorCode;

    public AccountActionException(BusinessErrorCodes errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }

    public BusinessErrorCodes getErrorCode() {
        return errorCode;
    }
}
