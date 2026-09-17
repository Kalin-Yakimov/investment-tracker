package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        ));
        properties.setAccessExpirationMinutes(30);
        properties.setIssuer("investment-tracker-test");
        properties.setAudience("investment-tracker-test-client");
        jwtService = new JwtService(properties);

        user = User.builder()
                .id(42)
                .email("user@example.com")
                .username("Test User")
                .enabled(true)
                .accountLocked(false)
                .roles(List.of())
                .build();
    }

    @Test
    void generatedAccessTokenContainsSessionAndExpiresAfterThirtyMinutes() {
        String token = jwtService.generateAccessToken(user, "session-123");
        var claims = jwtService.parseAndValidate(token);

        assertEquals(42, jwtService.extractUserId(claims));
        assertEquals("session-123", jwtService.extractSessionId(claims));
        assertEquals("user@example.com", claims.get("email", String.class));
        assertTrue(jwtService.isAccessTokenForUser(claims, user));
        assertEquals(1_800, claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond());
    }

    @Test
    void modifiedTokenIsRejected() {
        String token = jwtService.generateAccessToken(user, "session-123");
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'a' ? 'b' : 'a';
        parts[2] = replacement + parts[2].substring(1);
        String modified = String.join(".", parts);

        assertThrows(JwtException.class, () -> jwtService.parseAndValidate(modified));
    }
}
