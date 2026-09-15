package com.kalin.investment_tracker.security;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            delete from RefreshToken token
             where token.session.expiresAt < :cutoff
                or (token.session.revokedAt is not null and token.session.revokedAt < :cutoff)
            """)
    int deleteForInactiveSessionsBefore(@Param("cutoff") LocalDateTime cutoff);
}
