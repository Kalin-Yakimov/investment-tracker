package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.email.VerificationEmailRequested;
import com.kalin.investment_tracker.user.Token;
import com.kalin.investment_tracker.user.TokenRepository;
import com.kalin.investment_tracker.user.TokenType;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final VerificationCodeEncoder encoder;
    private final AuthRateLimitService limits;
    private final AuthProtectionProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void issue(User target, TokenType type) {
        User user = userRepository.findLockedById(target.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.isAccountLocked() || (type == TokenType.ACCOUNT_ACTIVATION && user.isEnabled())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<Token> previous = tokenRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), type);
        String code = freshCode(user.getId(), type, previous);
        invalidate(previous, now);
        Token token = tokenRepository.save(Token.builder()
                .token(encoder.encode(code, user.getId(), type))
                .user(user).type(type).createdAt(now)
                .expiresAt(now.plus(properties.getCodeLifetime())).build());
        events.publishEvent(new VerificationEmailRequested(token.getId(), user.getId(), user.getEmail(),
                user.fullName(), code, type));
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public User consume(String email, String code, TokenType type) {
        User user = userRepository.findLockedByEmail(AuthRateLimitService.normalizeEmail(email))
                .orElse(null);
        if (user == null) {
            limits.checkVerification(email, type);
            throw new InvalidVerificationCodeException();
        }
        // Use the resolved account ID, not spelling variants accepted by database collation.
        int attempt = limits.checkVerificationForUser(user.getId(), type);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Token> tokens = tokenRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), type);
        Token current = tokens.stream().filter(token -> token.getValidatedAt() == null).findFirst().orElse(null);
        boolean unexpired = current != null && current.getExpiresAt() != null && current.getExpiresAt().isAfter(now);
        if (!unexpired || !encoder.matches(code, current.getToken(), user.getId(), type)) {
            if (!unexpired || attempt >= properties.getAttemptsPerEmail()) {
                invalidate(tokens, now);
            }
            throw new InvalidVerificationCodeException();
        }
        if (user.isAccountLocked()) {
            throw new InvalidVerificationCodeException();
        }
        // The user-row lock serializes issuance, consumption and password changes for this account.
        invalidate(tokens, now);
        return user;
    }

    @Scheduled(cron = "${security.verification.cleanup-cron:0 0 4 * * ?}")
    @Transactional
    public void deleteOldExpiredCodes() {
        tokenRepository.deleteExpiredBefore(LocalDateTime.now(clock)
                .minusDays(properties.getExpiredCodeRetentionDays()));
    }

    @Transactional
    public void invalidatePasswordResetCodes(User user) {
        userRepository.findLockedById(user.getId()).orElseThrow(InvalidVerificationCodeException::new);
        invalidate(tokenRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(), TokenType.PASSWORD_RESET), LocalDateTime.now(clock));
    }

    private void invalidate(List<Token> tokens, LocalDateTime now) {
        List<Token> active = tokens.stream().filter(token -> token.getValidatedAt() == null).toList();
        active.forEach(token -> token.setValidatedAt(now));
        tokenRepository.saveAll(active);
    }

    private String freshCode(Integer userId, TokenType type, List<Token> previous) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            if (previous.stream().noneMatch(token -> encoder.matches(code, token.getToken(), userId, type))) {
                return code;
            }
        }
        throw new IllegalStateException("A verification code could not be issued. Please try again later.");
    }
}
