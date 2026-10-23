package com.kalin.investment_tracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRepository extends JpaRepository<Token, Integer> {

    List<Token> findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(Integer userId, TokenType type);

    @Modifying
    @Query("delete from Token token where token.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
