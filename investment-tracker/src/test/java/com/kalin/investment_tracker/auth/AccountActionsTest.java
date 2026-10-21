package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.handler.BusinessErrorCodes;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.AuthSessionService;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountActionsTest {
    @Mock RoleRepository roles;
    @Mock PasswordEncoder passwords;
    @Mock UserRepository users;
    @Mock VerificationCodeService codes;
    @Mock AuthRateLimitService limits;
    @Mock AuthenticationManager authentication;
    @Mock JwtService jwt;
    @Mock AuthSessionService sessions;
    @Mock ActivityEventService activity;
    @InjectMocks AuthenticationService service;

    private User lockedUser() {
        User user = User.builder().id(42).username("DisplayName").email("identity@example.invalid")
                .password("existing-hash").build();
        when(users.findLockedById(42)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void incorrectCurrentPasswordIsAnExpectedFailureWithNoAccountOrSessionMutation() {
        User user = lockedUser();
        var error = assertThrows(AccountActionException.class, () -> service.changePassword(
                change("incorrect", "new-password"), principal(user)));
        assertEquals(BusinessErrorCodes.INCORRECT_CURRENT_PASSWORD, error.getErrorCode());
        assertEquals("existing-hash", user.getPassword());
        verify(users, never()).save(any());
        verify(passwords, never()).encode(any());
        verifyNoInteractions(sessions, codes, activity);
    }

    @Test
    void differentConfirmationIsAnExpectedFailureAndDoesNotRevokeSessions() {
        User user = lockedUser();
        when(passwords.matches("legacy", "existing-hash")).thenReturn(true);
        ChangePasswordRequest request = change("legacy", "new-password");
        request.setConfirmationPassword("other-password");
        var error = assertThrows(AccountActionException.class, () -> service.changePassword(request, principal(user)));
        assertEquals(BusinessErrorCodes.NEW_PASSWORD_DOES_NOT_MATCH, error.getErrorCode());
        verify(users, never()).save(any());
        verifyNoInteractions(sessions, codes, activity);
    }

    @Test
    void usernameOwnedByTheSameAccountIsAllowedDespiteEmailBeingTheSecurityUsername() {
        User user = lockedUser();
        when(users.findByUsername("DisplayName")).thenReturn(Optional.of(user));
        when(jwt.generateAccessToken(user, "session")).thenReturn("fresh-access-token");
        var response = service.changeUsername(new ChangeUsernameRequest(" DisplayName "), principal(user), "session");
        assertEquals("fresh-access-token", response.getToken());
        assertEquals("identity@example.invalid", user.getUsername());
        assertEquals("DisplayName", user.fullName());
        verify(users).save(user);
    }

    @Test
    void usernameOwnedByAnotherAccountIsAConflictAndDoesNotChangeTheCurrentAccount() {
        User user = lockedUser();
        when(users.findByUsername("TakenName")).thenReturn(Optional.of(User.builder().id(43).build()));
        var error = assertThrows(AccountActionException.class, () -> service.changeUsername(
                new ChangeUsernameRequest("TakenName"), principal(user), "session"));
        assertEquals(BusinessErrorCodes.USERNAME_ALREADY_TAKEN, error.getErrorCode());
        assertEquals("DisplayName", user.fullName());
        verify(users, never()).save(any());
        verifyNoInteractions(jwt, activity);
    }

    @Test
    void invalidNewPasswordsAreRejectedEvenWhenCallingTheServiceDirectly() {
        assertThrows(IllegalArgumentException.class, () -> service.register(
                RegistrationRequest.builder().password("short").build()));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(
                ResetPasswordRequest.builder().newPassword("short").build()));
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(change("legacy", "short"), null));
        verifyNoInteractions(users, passwords, codes, sessions, activity);
    }

    private static ChangePasswordRequest change(String current, String next) {
        return ChangePasswordRequest.builder().currentPassword(current).newPassword(next).confirmationPassword(next).build();
    }

    private static UsernamePasswordAuthenticationToken principal(User user) {
        return new UsernamePasswordAuthenticationToken(user, null);
    }
}
