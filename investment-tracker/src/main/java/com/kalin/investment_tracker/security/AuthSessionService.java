package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import org.springframework.security.authentication.DisabledException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthSessionProperties properties;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedRefreshToken createSession(
            User user,
            boolean rememberMe,
            String userAgent,
            String ipAddress
    ) {
        user = userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new DisabledException("Account is unavailable"));
        if (!user.isEnabled() || user.isAccountLocked()) {
            throw new DisabledException("Account is unavailable");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = rememberMe
                ? now.plusDays(properties.getRememberedRefreshDays())
                : now.plusHours(properties.getSessionRefreshHours());

        AuthSession session = AuthSession.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .createdAt(now)
                .expiresAt(expiresAt)
                .lastUsedAt(now)
                .remembered(rememberMe)
                .userAgent(limit(userAgent, 512))
                .ipAddress(limit(ipAddress, 64))
                .build();
        authSessionRepository.save(session);

        return createRefreshToken(session, now, expiresAt);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedRefreshToken rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }

        String tokenHash = hash(rawRefreshToken);
        Integer ownerId = refreshTokenRepository.findOwnerIdByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        User user = userRepository.findLockedById(ownerId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Account is unavailable"));
        // Lock order for all auth operations: account -> refresh token -> session writes.
        RefreshToken currentToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        AuthSession session = currentToken.getSession();
        LocalDateTime now = LocalDateTime.now();

        if (currentToken.getUsedAt() != null || currentToken.getRevokedAt() != null) {
            revokeSession(session, now);
            throw new InvalidRefreshTokenException("Refresh token reuse detected");
        }

        if (!session.isActiveAt(now) || !currentToken.getExpiresAt().isAfter(now)) {
            revokeSession(session, now);
            throw new InvalidRefreshTokenException("Refresh session has expired");
        }

        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            revokeSession(session, now);
            throw new InvalidRefreshTokenException("User account is not active");
        }

        if (authSessionRepository.touchActiveSession(session.getId(), now) != 1) {
            throw new InvalidRefreshTokenException("Refresh session is inactive");
        }
        currentToken.setUsedAt(now);
        IssuedRefreshToken replacement = createRefreshToken(session, now, session.getExpiresAt());
        currentToken.setReplacedByTokenId(replacement.tokenId());
        refreshTokenRepository.save(currentToken);

        return new RotatedRefreshToken(
                user,
                session.getId(),
                replacement.rawToken(),
                session.isRemembered(),
                session.getExpiresAt()
        );
    }

    @Transactional
    public void revokeAllForUser(Integer userId) {
        userRepository.findLockedById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        authSessionRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    /** A retained, expired/revoked cookie must not terminate a later, unrelated login. */
    @Transactional
    public Optional<User> revokeAllForRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        String tokenHash = hash(rawToken);
        Optional<Integer> ownerId = refreshTokenRepository.findOwnerIdByTokenHash(tokenHash);
        if (ownerId.isEmpty()) return Optional.empty();
        Optional<User> user = userRepository.findLockedById(ownerId.get());
        if (user.isEmpty()) return Optional.empty();
        Optional<RefreshToken> token = refreshTokenRepository.findByTokenHash(tokenHash);
        LocalDateTime now = LocalDateTime.now();
        if (token.isEmpty() || token.get().getRevokedAt() != null
                || !token.get().getExpiresAt().isAfter(now)
                || !token.get().getSession().isActiveAt(now)
                || authSessionRepository.touchActiveSession(token.get().getSession().getId(), now) != 1) {
            return Optional.empty();
        }
        authSessionRepository.revokeAllForUser(user.get().getId(), now);
        return user;
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(String sessionId, Integer userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                sessionId,
                userId,
                LocalDateTime.now()
        );
    }

    @Scheduled(cron = "${security.auth.cleanup-cron:0 30 3 * * ?}")
    @Transactional
    public void deleteOldInactiveSessions() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(properties.getExpiredSessionRetentionDays());
        refreshTokenRepository.deleteForInactiveSessionsBefore(cutoff);
        authSessionRepository.deleteInactiveBefore(cutoff);
    }

    private IssuedRefreshToken createRefreshToken(
            AuthSession session,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        String rawToken = generateSecureToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .session(session)
                .tokenHash(hash(rawToken))
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
        refreshTokenRepository.save(refreshToken);
        return new IssuedRefreshToken(session, refreshToken.getId(), rawToken);
    }

    private void revokeSession(AuthSession session, LocalDateTime now) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(now);
            authSessionRepository.save(session);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record IssuedRefreshToken(AuthSession session, String tokenId, String rawToken) {
    }

    public record RotatedRefreshToken(
            User user,
            String sessionId,
            String rawToken,
            boolean remembered,
            LocalDateTime expiresAt
    ) {
    }
}
