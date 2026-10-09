package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.user.TokenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerificationCodeEncoderTest {
    private final VerificationCodeEncoder encoder =
            new VerificationCodeEncoder("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Test
    void storedValueIsNotTheCodeAndIsBoundToUserAndPurpose() {
        String encoded = encoder.encode("123456", 1, TokenType.ACCOUNT_ACTIVATION);
        assertNotEquals("123456", encoded);
        assertTrue(encoder.matches("123456", encoded, 1, TokenType.ACCOUNT_ACTIVATION));
        assertFalse(encoder.matches("123456", encoded, 2, TokenType.ACCOUNT_ACTIVATION));
        assertFalse(encoder.matches("123456", encoded, 1, TokenType.PASSWORD_RESET));
        assertFalse(encoder.matches("123457", encoded, 1, TokenType.ACCOUNT_ACTIVATION));
    }

    @Test
    void legacyCodeRemainsCompatibleButMalformedInputDoesNot() {
        assertTrue(encoder.matches("123456", "123456", 1, TokenType.ACCOUNT_ACTIVATION));
        assertFalse(encoder.matches("12345", "12345", 1, TokenType.ACCOUNT_ACTIVATION));
        assertFalse(encoder.matches(null, "123456", 1, TokenType.ACCOUNT_ACTIVATION));
    }

    @Test
    void weakOrMalformedSecretsAreRejectedWithoutEchoingTheSecret() {
        assertThrows(IllegalStateException.class, () -> new VerificationCodeEncoder("bad-secret"));
        assertThrows(IllegalStateException.class, () -> new VerificationCodeEncoder("YQ=="));
    }
}
