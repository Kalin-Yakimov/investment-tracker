package com.kalin.investment_tracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TokenRepository extends JpaRepository<Token, Integer> {

    Optional<Token> findByTokenAndType(String token, TokenType type);
    List<Token> findAllByUserIdAndTypeAndValidatedAtIsNull(Integer userId, TokenType type);
    boolean existsByTokenAndValidatedAtIsNull(String token);
}
