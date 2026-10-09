package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.user.TokenType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class VerificationCodeEncoder {
    private static final String PREFIX = "hmac-v1:";
    private final SecretKeySpec key;

    public VerificationCodeEncoder(@Value("${security.verification.secret:${security.jwt.secret}}") String secret) {
        try {
            byte[] bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32) {
                throw new IllegalArgumentException();
            }
            key = new SecretKeySpec(bytes, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Verification secret must contain at least 32 Base64-encoded bytes");
        }
    }

    public String encode(String code, Integer userId, TokenType type) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            String value = "investment-tracker:email-code:v1:" + userId + ":" + type + ":" + code;
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Verification code encoding failed", exception);
        }
    }

    public boolean matches(String code, String storedValue, Integer userId, TokenType type) {
        if (storedValue == null || code == null || !code.matches("[0-9]{6}")) {
            return false;
        }
        // Short-lived compatibility for already-issued legacy codes; new codes are never stored raw.
        String expected = storedValue.startsWith(PREFIX) ? encode(code, userId, type) : code;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                storedValue.getBytes(StandardCharsets.UTF_8));
    }
}
