package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    @Mock
    private AuthSessionRepository authSessionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;

    private AuthSessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setRememberedRefreshDays(60);
        properties.setSessionRefreshHours(24);
        service = new AuthSessionService(authSessionRepository, refreshTokenRepository, properties, userRepository);

        user = User.builder()
                .id(1)
                .email("user@example.com")
                .enabled(true)
                .accountLocked(false)
                .roles(List.of())
                .build();
    }

    @Test
    void rememberedSessionIsCreatedForSixtyDaysAndOnlyTokenHashIsStored() {
        when(userRepository.findLockedById(1)).thenReturn(Optional.of(user));
        var issued = service.createSession(user, true, "Browser", "127.0.0.1");

        assertTrue(issued.session().isRemembered());
        assertTrue(issued.session().getExpiresAt().isAfter(LocalDateTime.now().plusDays(59)));
        assertNotNull(issued.rawToken());

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken storedToken = tokenCaptor.getValue();
        assertNotEquals(issued.rawToken(), storedToken.getTokenHash());
        assertTrue(storedToken.getTokenHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void refreshTokenIsRotatedAndOldTokenIsMarkedAsUsed() throws Exception {
        String rawToken = "original-refresh-token";
        AuthSession session = activeSession();
        RefreshToken current = RefreshToken.builder()
                .id("token-1")
                .session(session)
                .tokenHash(sha256(rawToken))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(session.getExpiresAt())
                .build();
        when(refreshTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(current));
        when(refreshTokenRepository.findOwnerIdByTokenHash(sha256(rawToken))).thenReturn(Optional.of(1));
        when(userRepository.findLockedById(1)).thenReturn(Optional.of(user));
        when(authSessionRepository.touchActiveSession(any(), any())).thenReturn(1);

        var rotated = service.rotateRefreshToken(rawToken);

        assertNotNull(current.getUsedAt());
        assertNotNull(current.getReplacedByTokenId());
        assertNotEquals(rawToken, rotated.rawToken());
        assertFalse(rotated.rawToken().isBlank());
    }

    @Test
    void reuseOfRotatedTokenRevokesWholeSession() throws Exception {
        String rawToken = "already-used-token";
        AuthSession session = activeSession();
        RefreshToken usedToken = RefreshToken.builder()
                .id("token-1")
                .session(session)
                .tokenHash(sha256(rawToken))
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .expiresAt(session.getExpiresAt())
                .usedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(usedToken));
        when(refreshTokenRepository.findOwnerIdByTokenHash(sha256(rawToken))).thenReturn(Optional.of(1));
        when(userRepository.findLockedById(1)).thenReturn(Optional.of(user));

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.rotateRefreshToken(rawToken));
        assertNotNull(session.getRevokedAt());
    }

    @Test
    void activeSessionCheckIsDelegatedToDatabase() {
        when(authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                any(), any(), any()
        )).thenReturn(true);

        assertTrue(service.isSessionActive("session-1", 1));
    }

    private AuthSession activeSession() {
        return AuthSession.builder()
                .id("session-1")
                .user(user)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .lastUsedAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(LocalDateTime.now().plusDays(60))
                .remembered(true)
                .build();
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
