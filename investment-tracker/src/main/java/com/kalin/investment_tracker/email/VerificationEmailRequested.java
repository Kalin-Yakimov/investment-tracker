package com.kalin.investment_tracker.email;

import com.kalin.investment_tracker.user.TokenType;

public record VerificationEmailRequested(
        Integer tokenId, Integer userId, String email, String username, String code, TokenType type
) {
    @Override
    public String toString() {
        return "VerificationEmailRequested[tokenId=" + tokenId + ", type=" + type + "]";
    }
}
