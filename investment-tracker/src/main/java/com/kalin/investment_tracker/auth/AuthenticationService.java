package com.kalin.investment_tracker.auth;
import com.kalin.investment_tracker.email.EmailService;
import com.kalin.investment_tracker.email.EmailTemplateName;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.user.Token;
import com.kalin.investment_tracker.user.TokenRepository;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
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
    private String activationUrl = "http://localhost:3000/activate-account";

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
        var newToken = generateAndSaveActivationToken(user);

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.ACTIVATE_ACCOUNT,
                activationUrl,
                newToken,
                "Account activation"
        );
    }

    private String generateAndSaveActivationToken(User user) {
        String generatedToken = generateAndSaveActivationCode(6);
        var token = Token.builder()
                .token(generatedToken)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
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

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = (User) auth.getPrincipal();
        var claims = new HashMap<String, Object>();
        claims.put("fullName", user.fullName());
        claims.put("userId", user.getId());
        var jwtToken = jwtService.generateToken(claims, user);
        return AuthenticationResponse.builder()
                .token(jwtToken).build();
    }

    public void activateAccount(String token) throws MessagingException {
        Token savedToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
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

    public boolean validateToken(String token, UserDetails userDetails) {
        return jwtService.isTokenValid(token, userDetails);
    }

    public void forgotPassword(ForgotPasswordRequest request) throws MessagingException {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("No account found with email: " + request.getEmail()));

        String resetCode = generateAndSaveActivationToken(user);

        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.RESET_PASSWORD,
                "http://localhost:3000/forgot-password",
                resetCode,
                "Password Reset Request"
        );
    }

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("No account found with email: " + request.getEmail()));

        Token savedToken = tokenRepository.findByToken(request.getCode())
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset code"));

        if (!savedToken.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Invalid reset code for this account");
        }

        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
            throw new RuntimeException("Reset code has expired. Please request a new code.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

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
    }

    public AuthenticationResponse changeUsername(ChangeUsernameRequest request, java.security.Principal connectedUser) {
        var user = getAuthenticatedUser(connectedUser);

        String newUsername = request.getNewUsername().trim();
        if (userRepository.existsByUsername(newUsername) && !newUsername.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalStateException("Username is already taken by another account");
        }

        user.setUsername(newUsername);
        userRepository.save(user);

        var claims = new HashMap<String, Object>();
        claims.put("fullName", user.fullName());
        claims.put("userId", user.getId());
        var jwtToken = jwtService.generateToken(claims, user);

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }
}
