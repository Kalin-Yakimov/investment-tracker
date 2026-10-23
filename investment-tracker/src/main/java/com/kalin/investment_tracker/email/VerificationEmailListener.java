package com.kalin.investment_tracker.email;

import com.kalin.investment_tracker.user.TokenRepository;
import com.kalin.investment_tracker.user.TokenType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Slf4j
public class VerificationEmailListener {
    private final TaskExecutor executor;
    private final EmailService emailService;
    private final TokenRepository tokenRepository;
    private final EmailDeliveryAuditService auditService;
    private final Clock clock;
    private final String frontendBaseUrl;

    public VerificationEmailListener(
            @Qualifier("verificationEmailExecutor") TaskExecutor executor,
            EmailService emailService, TokenRepository tokenRepository,
            EmailDeliveryAuditService auditService, Clock clock,
            @Value("${app.frontend.base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.executor = executor;
        this.emailService = emailService;
        this.tokenRepository = tokenRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(VerificationEmailRequested event) {
        try {
            executor.execute(() -> deliver(event));
        } catch (RuntimeException exception) {
            recordFailure(event, exception);
        }
    }

    private void deliver(VerificationEmailRequested event) {
        try {
            var token = tokenRepository.findById(event.tokenId()).orElse(null);
            if (token == null || token.getValidatedAt() != null || token.getExpiresAt() == null
                    || !token.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
                return; // Do not send a queued code that was consumed, superseded or expired.
            }
            boolean activation = event.type() == TokenType.ACCOUNT_ACTIVATION;
            emailService.sendEmail(event.email(), event.username(),
                    activation ? EmailTemplateName.ACTIVATE_ACCOUNT : EmailTemplateName.RESET_PASSWORD,
                    frontendBaseUrl + (activation ? "/activate-account" : "/forgot-password"),
                    event.code(), activation ? "Account activation" : "Password Reset Request");
        } catch (Exception exception) {
            recordFailure(event, exception);
        }
    }

    private void recordFailure(VerificationEmailRequested event, Exception exception) {
        // SMTP exceptions may contain recipients or message contents; never log their text/stack.
        log.warn("Verification email failed for token id {} ({})", event.tokenId(),
                exception.getClass().getSimpleName());
        try {
            auditService.recordFailure(event.userId());
        } catch (RuntimeException auditFailure) {
            log.error("Verification email failure audit could not be saved for token id {}", event.tokenId());
        }
    }
}
