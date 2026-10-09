package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.user.TokenType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitServiceTest {
    @Test
    void fiveAttemptsAreAllowedAndNextIsBlockedEvenWithEmailCaseChanges() {
        AuthRateLimitService service = new AuthRateLimitService(new AuthProtectionProperties());
        for (int i = 1; i <= 5; i++) {
            assertEquals(i, service.checkVerification(" Person@Example.com ", TokenType.PASSWORD_RESET));
        }
        assertThrows(AuthRateLimitException.class,
                () -> service.checkVerification("person@example.com", TokenType.PASSWORD_RESET));
    }

    @Test
    void resendCooldownAndHourlyBudgetAreBothApplied() {
        AtomicLong time = new AtomicLong();
        AuthRateLimitService service = new AuthRateLimitService(new AuthProtectionProperties(), time::get);
        service.checkCodeRequest("person@example.com");
        assertEquals(60, assertThrows(AuthRateLimitException.class,
                () -> service.checkCodeRequest("person@example.com")).getRetryAfterSeconds());
        time.addAndGet(Duration.ofMinutes(1).toNanos());
        service.checkCodeRequest("person@example.com");
        time.addAndGet(Duration.ofMinutes(1).toNanos());
        service.checkCodeRequest("person@example.com");
        time.addAndGet(Duration.ofMinutes(1).toNanos());
        assertThrows(AuthRateLimitException.class, () -> service.checkCodeRequest("person@example.com"));
        time.set(Duration.ofHours(1).toNanos());
        service.checkCodeRequest("person@example.com");
    }

    @Test
    void attemptWindowExpiresWithoutSleeping() {
        AtomicLong time = new AtomicLong();
        AuthRateLimitService service = new AuthRateLimitService(new AuthProtectionProperties(), time::get);
        for (int i = 0; i < 5; i++) service.checkVerification("person@example.com", TokenType.ACCOUNT_ACTIVATION);
        assertThrows(AuthRateLimitException.class,
                () -> service.checkVerification("person@example.com", TokenType.ACCOUNT_ACTIVATION));
        time.set(Duration.ofMinutes(15).toNanos());
        assertEquals(1, service.checkVerification("person@example.com", TokenType.ACCOUNT_ACTIVATION));
    }

    @Test
    void capacityFailsClosedInsteadOfEvictingAnExistingBudget() {
        AuthProtectionProperties properties = new AuthProtectionProperties();
        properties.setMaximumLimitEntries(2);
        properties.setAttemptsPerEmail(1);
        AuthRateLimitService service = new AuthRateLimitService(properties);
        service.checkVerification("first@example.com", TokenType.PASSWORD_RESET);
        service.checkVerification("second@example.com", TokenType.PASSWORD_RESET);
        assertThrows(AuthRateLimitException.class,
                () -> service.checkVerification("third@example.com", TokenType.PASSWORD_RESET));
        assertThrows(AuthRateLimitException.class,
                () -> service.checkVerification("first@example.com", TokenType.PASSWORD_RESET));
    }

    @Test
    void oneIpCannotBypassRegistrationBudgetWithDifferentAccounts() {
        AuthRateLimitService service = new AuthRateLimitService(new AuthProtectionProperties());
        for (int i = 0; i < 5; i++) service.checkIp(AuthRateLimitService.Operation.REGISTER, "127.0.0.1");
        assertThrows(AuthRateLimitException.class,
                () -> service.checkIp(AuthRateLimitService.Operation.REGISTER, "127.0.0.1"));
    }

    @Test
    void loginBudgetIsSeparateFromCodeVerification() {
        AuthRateLimitService service = new AuthRateLimitService(new AuthProtectionProperties());
        for (int i = 0; i < 10; i++) service.checkLogin("person@example.com");
        assertThrows(AuthRateLimitException.class, () -> service.checkLogin("person@example.com"));
        assertEquals(1, service.checkVerification("person@example.com", TokenType.PASSWORD_RESET));
    }

    @Test
    void resolvedAccountBudgetsAreIndependentOfEmailSpellingAndSharedAcrossSenders() {
        AuthRateLimitService service = new AuthRateLimitService(new AuthProtectionProperties());
        service.checkCodeRequestForUser(1);
        assertThrows(AuthRateLimitException.class, () -> service.checkCodeRequestForUser(1));
        service.checkCodeRequestForUser(2);
        for (int i = 0; i < 5; i++) service.checkVerificationForUser(1, TokenType.PASSWORD_RESET);
        assertThrows(AuthRateLimitException.class, () -> service.checkVerificationForUser(1, TokenType.PASSWORD_RESET));
        assertEquals(1, service.checkVerificationForUser(2, TokenType.PASSWORD_RESET));
    }
}
