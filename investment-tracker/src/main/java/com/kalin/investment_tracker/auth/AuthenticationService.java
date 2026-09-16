package com.kalin.investment_tracker.auth;
import com.kalin.investment_tracker.email.EmailService;
import com.kalin.investment_tracker.email.EmailTemplateName;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.AuthSessionService;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.user.Token;
import com.kalin.investment_tracker.user.TokenRepository;
import com.kalin.investment_tracker.user.TokenType;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public void register(RegistrationRequest request) throws MessagingException {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username is already taken. Please choose another.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already registered. Please sign in.");
        }

        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Role USER was not initialized"));
        var user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .accountLocked(false)
                .enabled(false)
                .roles(List.of(userRole))
                .build();
        userRepository.save(user);
        sendValidationEmail(user);
    }

    private void sendValidationEmail(User user) throws MessagingException {
        var newToken = generateAndSaveVerificationToken(user, TokenType.ACCOUNT_ACTIVATION);

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.ACTIVATE_ACCOUNT,
                frontendBaseUrl + "/activate-account",
                newToken,
                "Account activation"
        );
    }

    private String generateAndSaveVerificationToken(User user, TokenType type) {
        LocalDateTime now = LocalDateTime.now();
        var activeTokens = tokenRepository.findAllByUserIdAndTypeAndValidatedAtIsNull(user.getId(), type);
        activeTokens.forEach(existingToken -> existingToken.setValidatedAt(now));
        tokenRepository.saveAll(activeTokens);

        String generatedToken;
        do {
            generatedToken = generateAndSaveActivationCode(6);
        } while (tokenRepository.existsByTokenAndValidatedAtIsNull(generatedToken));

        var token = Token.builder()
                .token(generatedToken)
                .createdAt(now)
                .expiresAt(now.plusMinutes(15))
                .type(type)
                .user(user)
                .build();
        tokenRepository.save(token);
        return generatedToken;
    }

    private String generateAndSaveActivationCode(int length) {
        String characters = "0123456789";
        StringBuilder codeBuilder = new StringBuilder();
        SecureRandom secureRandom = new SecureRandom();
        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(characters.length());
            codeBuilder.append(characters.charAt(randomIndex));
        }
        return codeBuilder.toString();
    }

    public AuthenticationResult authenticate(
            AuthenticationRequest request,
            String userAgent,
            String ipAddress
    ) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = (User) auth.getPrincipal();
        var issuedRefreshToken = authSessionService.createSession(
                user,
                request.isRememberMe(),
                userAgent,
                ipAddress
        );
        var session = issuedRefreshToken.session();

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

    public void logout(java.security.Principal connectedUser) {
        authSessionService.revokeAllForUser(getAuthenticatedUser(connectedUser).getId());
    }

    public void activateAccount(String token) throws MessagingException {
        Token savedToken = tokenRepository.findByTokenAndType(token, TokenType.ACCOUNT_ACTIVATION)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
        if (savedToken.getValidatedAt() != null) {
            throw new RuntimeException("Activation code has already been used");
        }
        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
            savedToken.setValidatedAt(LocalDateTime.now());
            tokenRepository.save(savedToken);
            sendValidationEmail(savedToken.getUser());
            throw new RuntimeException("Activation token has expired. A new token has been sent to the same email");
        }
        var user = userRepository.findById(savedToken.getUser().getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        user.setEnabled(true);
        userRepository.save(user);
        savedToken.setValidatedAt(LocalDateTime.now());
        tokenRepository.save(savedToken);
    }

    public void forgotPassword(ForgotPasswordRequest request) throws MessagingException {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return;
        }

        String resetCode = generateAndSaveVerificationToken(user, TokenType.PASSWORD_RESET);

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.RESET_PASSWORD,
                frontendBaseUrl + "/forgot-password",
                resetCode,
                "Password Reset Request"
        );
    }

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("No account found with email: " + request.getEmail()));

        Token savedToken = tokenRepository.findByTokenAndType(request.getCode(), TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset code"));

        if (savedToken.getValidatedAt() != null) {
            throw new RuntimeException("Reset code has already been used");
        }

        if (!savedToken.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Invalid reset code for this account");
        }

        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
            savedToken.setValidatedAt(LocalDateTime.now());
            tokenRepository.save(savedToken);
            throw new RuntimeException("Reset code has expired. Please request a new code.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        authSessionService.revokeAllForUser(user.getId());

        savedToken.setValidatedAt(LocalDateTime.now());
        tokenRepository.save(savedToken);
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

    public void changePassword(ChangePasswordRequest request, java.security.Principal connectedUser) {
        var user = getAuthenticatedUser(connectedUser);

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalStateException("Wrong current password");
        }

        // Verify confirmation password match
        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new IllegalStateException("New passwords do not match");
        }

        // Save encoded new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        authSessionService.revokeAllForUser(user.getId());
    }

    public AuthenticationResponse changeUsername(
            ChangeUsernameRequest request,
            java.security.Principal connectedUser,
            String sessionId
    ) {
        var user = getAuthenticatedUser(connectedUser);

        String newUsername = request.getNewUsername().trim();
        if (userRepository.existsByUsername(newUsername) && !newUsername.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalStateException("Username is already taken by another account");
        }

        user.setUsername(newUsername);
        userRepository.save(user);

        return buildAuthenticationResponse(user, sessionId);
    }

    private AuthenticationResponse buildAuthenticationResponse(User user, String sessionId) {
        return AuthenticationResponse.builder()
                .token(jwtService.generateAccessToken(user, sessionId))
                .expiresInSeconds(jwtService.getAccessExpirationSeconds())
                .build();
    }
}
