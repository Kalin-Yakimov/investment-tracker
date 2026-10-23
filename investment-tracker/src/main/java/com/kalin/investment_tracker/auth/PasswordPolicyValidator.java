package com.kalin.investment_tracker.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {
    private boolean newPassword;

    @Override
    public void initialize(ValidPassword annotation) {
        newPassword = annotation.newPassword();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String error = PasswordPolicy.validationError(value, newPassword);
        if (error == null) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }
}
