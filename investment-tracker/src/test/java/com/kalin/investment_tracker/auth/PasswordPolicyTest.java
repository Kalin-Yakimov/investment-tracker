package com.kalin.investment_tracker.auth;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    static Stream<Arguments> newPasswords() {
        return Stream.of(
                Arguments.of(null, PasswordPolicy.REQUIRED),
                Arguments.of("", PasswordPolicy.REQUIRED),
                Arguments.of(" ".repeat(10), PasswordPolicy.REQUIRED),
                Arguments.of("a".repeat(9), PasswordPolicy.TOO_SHORT),
                Arguments.of("a".repeat(10), null),
                Arguments.of("a".repeat(72), null),
                Arguments.of("a".repeat(73), PasswordPolicy.TOO_LONG),
                Arguments.of("я".repeat(10), null),
                Arguments.of("я".repeat(36), null),
                Arguments.of("я".repeat(37), PasswordPolicy.TOO_LONG),
                Arguments.of("😀".repeat(9), PasswordPolicy.TOO_SHORT),
                Arguments.of("😀".repeat(10), null),
                Arguments.of("😀".repeat(18), null),
                Arguments.of("😀".repeat(19), PasswordPolicy.TOO_LONG),
                Arguments.of("  a passphrase  ", null),
                Arguments.of("\uD800" + "a".repeat(10), PasswordPolicy.INVALID_TEXT)
        );
    }

    @ParameterizedTest
    @MethodSource("newPasswords")
    void policyUsesUnicodeCharactersAndActualUtf8Bytes(String password, String expected) {
        assertEquals(expected, PasswordPolicy.validationError(password, true));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (Object request : List.of(
                    RegistrationRequest.builder().username("test-user").email("test@example.invalid").password(password).build(),
                    ResetPasswordRequest.builder().email("test@example.invalid").code("123456").newPassword(password).build(),
                    ChangePasswordRequest.builder().currentPassword("legacy").newPassword(password)
                            .confirmationPassword("test-password").build())) {
                var errors = validator.validate(request);
                if (expected == null) assertTrue(errors.isEmpty());
                else {
                    assertEquals(1, errors.size());
                    assertEquals(expected, errors.iterator().next().getMessage());
                }
            }
        }
    }

    @Test
    void legacyShortCredentialsCanStillBeValidatedForSignInAndCurrentPassword() {
        assertNull(PasswordPolicy.validationError("legacy", false));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(AuthenticationRequest.builder()
                    .email("test@example.invalid").password("legacy").build()).isEmpty());
            assertFalse(factory.getValidator().validate(AuthenticationRequest.builder()
                    .email("test@example.invalid").password("я".repeat(37)).build()).isEmpty());
        }
    }

    @Test
    void boundaryPasswordsWorkWithTheRealEncoderWithoutNormalizationOrTruncation() {
        var encoder = new BCryptPasswordEncoder();
        for (String password : List.of("a".repeat(72), "я".repeat(36), "😀".repeat(18), "  a passphrase  ")) {
            PasswordPolicy.requireValid(password, true);
            String hash = encoder.encode(password);
            assertTrue(encoder.matches(password, hash));
            assertFalse(encoder.matches("different-password", hash));
        }
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.requireValid("a".repeat(73), true));
    }
}
