package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AuthSessionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void activeSessionCanBeRevokedAndItsRefreshTokenCanBeFound() {
        User user = User.builder()
                .username("test-user")
                .email("test@example.com")
                .password("encoded-password")
                .enabled(true)
                .accountLocked(false)
                .roles(new ArrayList<>())
                .items(new ArrayList<>())
                .createdDate(LocalDateTime.now())
                .build();
        entityManager.persist(user);

        AuthSession session = AuthSession.builder()
                .id("session-1")
                .user(user)
                .createdAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(1))
                .remembered(false)
                .build();
        authSessionRepository.save(session);

        RefreshToken token = RefreshToken.builder()
                .id("refresh-1")
                .session(session)
                .tokenHash("a".repeat(64))
                .createdAt(LocalDateTime.now())
                .expiresAt(session.getExpiresAt())
                .build();
        refreshTokenRepository.save(token);
        entityManager.flush();

        assertTrue(refreshTokenRepository.findByTokenHash("a".repeat(64)).isPresent());
        assertTrue(authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                session.getId(), user.getId(), LocalDateTime.now()
        ));

        authSessionRepository.revokeAllForUser(user.getId(), LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertFalse(authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                session.getId(), user.getId(), LocalDateTime.now()
        ));
    }
}
