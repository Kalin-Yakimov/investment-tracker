package com.kalin.investment_tracker.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name ="Authentication")
public class AuthenticationController {

    private final AuthenticationService service;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<?> register(
            @RequestBody @Valid RegistrationRequest request
    ) throws MessagingException {
        service.register(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody @Valid AuthenticationRequest request
    ) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    @GetMapping("/activate-account")
    public void confirm(
            @RequestParam String token
    ) throws MessagingException {
        service.activateAccount(token);
    }

    @GetMapping("/validate-token")
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String tokenHeader) {
        try {
            String token = tokenHeader.substring("Bearer ".length());
            if (service.validateToken(token, null)) { // Pass null for UserDetails if not needed
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest request
    ) throws MessagingException {
        service.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody @Valid ResetPasswordRequest request
    ) {
        service.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody @Valid ChangePasswordRequest request,
            java.security.Principal connectedUser
    ) {
        service.changePassword(request, connectedUser);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/change-username")
    public ResponseEntity<AuthenticationResponse> changeUsername(
            @RequestBody @Valid ChangeUsernameRequest request,
            java.security.Principal connectedUser
    ) {
        return ResponseEntity.ok(service.changeUsername(request, connectedUser));
    }

}
