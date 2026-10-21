package com.kalin.investment_tracker.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update AuthSession session set session.lastUsedAt = :now
             where session.id = :id and session.revokedAt is null and session.expiresAt > :now
            """)
    int touchActiveSession(@Param("id") String id, @Param("now") LocalDateTime now);

    boolean existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            String id,
            Integer userId,
            LocalDateTime now
    );

    @Modifying(flushAutomatically = true)
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

    long countByRevokedAtIsNullAndExpiresAtAfter(LocalDateTime now);

    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Integer userId, LocalDateTime now);

    List<AuthSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
            Integer userId,
            LocalDateTime now
    );

    @Query("""
            select max(session.lastUsedAt) from AuthSession session
             where session.user.id = :userId
            """)
    LocalDateTime findLastActivityForUser(@Param("userId") Integer userId);
}
