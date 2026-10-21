package com.kalin.investment_tracker.auth;
import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.AuthSessionService;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.user.TokenType;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.kalin.investment_tracker.handler.BusinessErrorCodes.*;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodes;
    private final AuthRateLimitService rateLimits;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final ActivityEventService activityEventService;

    @Transactional
    public void register(RegistrationRequest request) throws MessagingException {
        PasswordPolicy.requireValid(request.getPassword(), true);
        String email = AuthRateLimitService.normalizeEmail(request.getEmail());
        rateLimits.checkCodeRequest(email);
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken. Please choose another.");
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("Email is already registered. Please sign in or request an activation code.");
        }

        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Role USER was not initialized"));
        var user = User.builder()
                .username(request.getUsername())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .accountLocked(false)
                .enabled(false)
                .roles(List.of(userRole))
                .build();
        userRepository.save(user);
        rateLimits.checkCodeRequestForUser(user.getId());
        activityEventService.recordUserAction(user, "ACCOUNT_REGISTERED", "Created a new account", null);
        verificationCodes.issue(user, TokenType.ACCOUNT_ACTIVATION);
    }

    @Transactional
    public AuthenticationResult authenticate(
            AuthenticationRequest request,
            String userAgent,
            String ipAddress
    ) {
        PasswordPolicy.requireValid(request.getPassword(), false);
        String email = AuthRateLimitService.normalizeEmail(request.getEmail());
        // Hold the account lock from password verification through session creation/commit.
        var account = userRepository.findLockedByEmail(email);
        if (account.isPresent()) rateLimits.checkLoginForUser(account.get().getId());
        else rateLimits.checkLogin(email);
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.getPassword()
                )
        );
        // Never create a session for an account that was not locked before authentication.
        var user = account.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.getId().equals(((User) auth.getPrincipal()).getId())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var issuedRefreshToken = authSessionService.createSession(
                user,
                request.isRememberMe(),
                userAgent,
                ipAddress
        );
        var session = issuedRefreshToken.session();
        activityEventService.recordSecurityEvent(user, "LOGIN_SUCCESS", "Created a new authenticated session", ipAddress);

        return new AuthenticationResult(
                buildAuthenticationResponse(user, session.getId()),
                issuedRefreshToken.rawToken(),
                session.isRemembered(),
                session.getExpiresAt()
        );
    }

    public AuthenticationResult refresh(String refreshToken) {
        var rotated = authSessionService.rotateRefreshToken(refreshToken);
        return new AuthenticationResult(
                buildAuthenticationResponse(rotated.user(), rotated.sessionId()),
                rotated.rawToken(),
                rotated.remembered(),
                rotated.expiresAt()
        );
    }

    @Transactional
    public void logout(java.security.Principal connectedUser, String refreshToken) {
        User user;
        if (connectedUser != null) {
            user = lockAuthenticatedUser(connectedUser);
            authSessionService.revokeAllForUser(user.getId());
        } else {
            user = authSessionService.revokeAllForRefreshToken(refreshToken).orElse(null);
        }
        if (user != null) {
            activityEventService.recordSecurityEvent(user, "SIGNED_OUT", "Revoked all active sessions", null);
        }
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void activateAccount(ActivationRequest request) {
        User user = verificationCodes.consume(request.email(), request.code(), TokenType.ACCOUNT_ACTIVATION);
        user.setEnabled(true);
        userRepository.save(user);
        activityEventService.recordSecurityEvent(user, "ACCOUNT_ACTIVATED", "Activated the account", null);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) throws MessagingException {
        String email = AuthRateLimitService.normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        checkCodeRequestBudget(email, user);
        if (user == null || user.isAccountLocked()) {
            return;
        }
        verificationCodes.issue(user, TokenType.PASSWORD_RESET);
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void resetPassword(ResetPasswordRequest request) {
        // Reject invalid new credentials before consuming a valid one-time reset code.
        PasswordPolicy.requireValid(request.getNewPassword(), true);
        User user = verificationCodes.consume(request.getEmail(), request.getCode(), TokenType.PASSWORD_RESET);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        authSessionService.revokeAllForUser(user.getId());
        activityEventService.recordSecurityEvent(user, "PASSWORD_RESET", "Reset the account password", null);
    }

    @Transactional
    public void resendActivation(ForgotPasswordRequest request) {
        String email = AuthRateLimitService.normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        checkCodeRequestBudget(email, user);
        if (user != null && !user.isEnabled() && !user.isAccountLocked()) {
            verificationCodes.issue(user, TokenType.ACCOUNT_ACTIVATION);
        }
    }

    private User getAuthenticatedUser(java.security.Principal connectedUser) {
        if (connectedUser != null) {
            if (connectedUser instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token) {
                if (token.getPrincipal() instanceof User u) {
                    return u;
                }
            }
            return userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new IllegalStateException("User not found"));
        }

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User u) {
            return u;
        }
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new IllegalStateException("User not found"));
        }

        throw new IllegalStateException("No authenticated user found");
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, java.security.Principal connectedUser) {
        PasswordPolicy.requireValid(request.getNewPassword(), true);
        PasswordPolicy.requireValid(request.getCurrentPassword(), false);
        PasswordPolicy.requireValid(request.getConfirmationPassword(), false);
        var user = lockAuthenticatedUser(connectedUser);

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AccountActionException(INCORRECT_CURRENT_PASSWORD);
        }

        // Verify confirmation password match
        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new AccountActionException(NEW_PASSWORD_DOES_NOT_MATCH);
        }

        // Save encoded new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        authSessionService.revokeAllForUser(user.getId());
        verificationCodes.invalidatePasswordResetCodes(user);
        activityEventService.recordSecurityEvent(user, "PASSWORD_CHANGED", "Changed the account password", null);
    }

    @Transactional
    public AuthenticationResponse changeUsername(
            ChangeUsernameRequest request,
            java.security.Principal connectedUser,
            String sessionId
    ) {
        var user = lockAuthenticatedUser(connectedUser);

        String newUsername = request.getNewUsername().trim();
        // getUsername() is the email identity used by Spring Security, not the display name.
        // Comparing immutable owners also respects the actual database's username collation.
        var owner = userRepository.findByUsername(newUsername);
        if (owner.isPresent() && !Objects.equals(owner.get().getId(), user.getId())) {
            throw new AccountActionException(USERNAME_ALREADY_TAKEN);
        }

        String previousUsername = user.fullName();
        user.setUsername(newUsername);
        userRepository.save(user);
        activityEventService.recordUserAction(
                user,
                "USERNAME_CHANGED",
                "Changed username from '" + previousUsername + "' to '" + newUsername + "'",
                null
        );

        return buildAuthenticationResponse(user, sessionId);
    }

    private AuthenticationResponse buildAuthenticationResponse(User user, String sessionId) {
        return AuthenticationResponse.builder()
                .token(jwtService.generateAccessToken(user, sessionId))
                .expiresInSeconds(jwtService.getAccessExpirationSeconds())
                .build();
    }

    @Transactional
    public void resendActivationFor(User user) throws MessagingException {
        if (user.isEnabled()) {
            throw new IllegalArgumentException("The account is already activated");
        }
        rateLimits.checkCodeRequestForUser(user.getId());
        verificationCodes.issue(user, TokenType.ACCOUNT_ACTIVATION);
    }

    private User lockAuthenticatedUser(java.security.Principal principal) {
        return userRepository.findLockedById(getAuthenticatedUser(principal).getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private void checkCodeRequestBudget(String email, User user) {
        if (user == null) rateLimits.checkCodeRequest(email);
        else rateLimits.checkCodeRequestForUser(user.getId());
    }

}
