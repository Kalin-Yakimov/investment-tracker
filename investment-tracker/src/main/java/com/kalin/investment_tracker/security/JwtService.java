package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String SESSION_ID_CLAIM = "sid";

    private final JwtProperties properties;

    public String generateAccessToken(User user, String sessionId) {
        long now = System.currentTimeMillis();
        long expiresAt = now + getAccessExpirationSeconds() * 1_000;
        var authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .issuedAt(new Date(now))
                .expiration(new Date(expiresAt))
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim("email", user.getEmail())
                .claim("fullName", user.fullName())
                .claim("authorities", authorities)
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token);
        return parsed.getPayload();
    }

    public boolean isAccessTokenForUser(Claims claims, User user) {
        return ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                && String.valueOf(user.getId()).equals(claims.getSubject());
    }

    public Integer extractUserId(Claims claims) {
        return Integer.valueOf(claims.getSubject());
    }

    public String extractUserId(String token) {
        return String.valueOf(extractUserId(parseAndValidate(token)));
    }

    public String extractSessionId(Claims claims) {
        return claims.get(SESSION_ID_CLAIM, String.class);
    }

    public long getAccessExpirationSeconds() {
        return properties.getAccessExpirationMinutes() * 60;
    }

    private SecretKey getSignInKey() {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured");
        }
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

