package com.kalin.investment_tracker.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    boolean existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            String id,
            Integer userId,
            LocalDateTime now
    );

    @Modifying
    @Query("""
            update AuthSession session
               set session.revokedAt = :revokedAt
             where session.user.id = :userId
               and session.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Integer userId, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("""
            delete from AuthSession session
             where session.expiresAt < :cutoff
                or (session.revokedAt is not null and session.revokedAt < :cutoff)
            """)
    int deleteInactiveBefore(@Param("cutoff") LocalDateTime cutoff);
}
