package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.security.AuthSessionProperties;
import com.kalin.investment_tracker.security.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationControllerTest {

    private AuthenticationService authenticationService;
    private AuthenticationController controller;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setCookieSecure(true);
        properties.setCookieSameSite("Strict");
        controller = new AuthenticationController(authenticationService, properties);
    }

    @Test
    void loginPlacesRefreshTokenOnlyInProtectedCookie() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("user@example.com")
                .password("password")
                .rememberMe(true)
                .build();
        AuthenticationResponse responseBody = AuthenticationResponse.builder()
                .token("access-token")
                .expiresInSeconds(1_800)
                .build();
        when(authenticationService.authenticate(request, "Browser", "127.0.0.1"))
                .thenReturn(new AuthenticationResult(
                        responseBody,
                        "refresh-token",
                        true,
                        LocalDateTime.now().plusDays(60)
                ));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("User-Agent")).thenReturn("Browser");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        var response = controller.authenticate(request, servletRequest);
        String cookie = response.getHeaders().getFirst("Set-Cookie");

        assertNotNull(cookie);
        assertTrue(cookie.contains("refresh_token=refresh-token"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Max-Age="));
        assertEquals("access-token", response.getBody().getToken());
        assertFalse(response.getBody().getToken().contains("refresh-token"));
    }

    @Test
    void invalidRefreshClearsCookie() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(authenticationService.refresh(null))
                .thenThrow(new InvalidRefreshTokenException("invalid"));

        var response = controller.refresh(request);
        String cookie = response.getHeaders().getFirst("Set-Cookie");

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(cookie);
        assertTrue(cookie.contains("Max-Age=0"));
    }
}
