package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.security.AuthSessionProperties;
import com.kalin.investment_tracker.security.InvalidRefreshTokenException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name ="Authentication")
public class AuthenticationController {

    private final AuthenticationService service;
    private final AuthSessionProperties authSessionProperties;
    private final AuthRateLimitService rateLimits;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> register(
            @RequestBody @Valid RegistrationRequest request,
            HttpServletRequest httpRequest
    ) throws MessagingException {
        rateLimits.checkIp(AuthRateLimitService.Operation.REGISTER, httpRequest.getRemoteAddr());
        service.register(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody @Valid AuthenticationRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimits.checkIp(AuthRateLimitService.Operation.LOGIN, httpRequest.getRemoteAddr());
        AuthenticationResult result = service.authenticate(
                request,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return withRefreshCookie(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(HttpServletRequest request) {
        try {
            return withRefreshCookie(service.refresh(readRefreshToken(request)));
        } catch (InvalidRefreshTokenException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                    .build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal connectedUser) {
        service.logout(connectedUser);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/activate-account")
    public void confirm(
            @RequestBody @Valid ActivationRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimits.checkIp(AuthRateLimitService.Operation.VERIFY_CODE, httpRequest.getRemoteAddr());
        service.activateAccount(request);
    }

    @GetMapping("/validate-token")
    public ResponseEntity<Void> validateToken() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) throws MessagingException {
        rateLimits.checkIp(AuthRateLimitService.Operation.REQUEST_CODE, httpRequest.getRemoteAddr());
        service.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @RequestBody @Valid ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimits.checkIp(AuthRateLimitService.Operation.VERIFY_CODE, httpRequest.getRemoteAddr());
        service.resetPassword(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/resend-activation")
    public ResponseEntity<Void> resendActivation(
            @RequestBody @Valid ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimits.checkIp(AuthRateLimitService.Operation.REQUEST_CODE, httpRequest.getRemoteAddr());
        service.resendActivation(request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @RequestBody @Valid ChangePasswordRequest request,
            Principal connectedUser
    ) {
        service.changePassword(request, connectedUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PatchMapping("/change-username")
    public ResponseEntity<AuthenticationResponse> changeUsername(
            @RequestBody @Valid ChangeUsernameRequest request,
            Principal connectedUser,
            @RequestAttribute("authSessionId") String sessionId
    ) {
        return ResponseEntity.ok(service.changeUsername(request, connectedUser, sessionId));
    }

    private ResponseEntity<AuthenticationResponse> withRefreshCookie(AuthenticationResult result) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        buildRefreshCookie(
                                result.refreshToken(),
                                result.remembered(),
                                result.refreshExpiresAt()
                        ).toString()
                )
                .body(result.response());
    }

    private ResponseCookie buildRefreshCookie(
            String value,
            boolean remembered,
            LocalDateTime expiresAt
    ) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(authSessionProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(authSessionProperties.isCookieSecure())
                .sameSite(authSessionProperties.getCookieSameSite())
                .path("/api/v1/auth");

        if (remembered) {
            long maxAgeSeconds = Math.max(0, Duration.between(LocalDateTime.now(), expiresAt).toSeconds());
            builder.maxAge(Duration.ofSeconds(maxAgeSeconds));
        }
        return builder.build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie
                .from(authSessionProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(authSessionProperties.isCookieSecure())
                .sameSite(authSessionProperties.getCookieSameSite())
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authSessionProperties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
