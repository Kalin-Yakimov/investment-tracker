package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.security.AuthSessionProperties;
import com.kalin.investment_tracker.security.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.kalin.investment_tracker.handler.GlobalExceptionHandler;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.doThrow;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationControllerTest {

    private AuthenticationService authenticationService;
    private AuthenticationController controller;
    private AuthRateLimitService limits;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setCookieSecure(true);
        properties.setCookieSameSite("Strict");
        limits = mock(AuthRateLimitService.class);
        controller = new AuthenticationController(authenticationService, properties, limits);
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

    @ParameterizedTest
    @CsvSource({"true,true,Strict", "false,false,Lax", "true,false,Strict", "false,true,Lax"})
    void issuingAndClearingCookieShareAllConfiguredAttributes(boolean remembered, boolean secure,
                                                            String sameSite) {
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setRefreshCookieName("project_session");
        properties.setCookieSecure(secure);
        properties.setCookieSameSite(sameSite);
        var configuredController = new AuthenticationController(authenticationService, properties, limits);
        var body = AuthenticationResponse.builder().token("access-token").expiresInSeconds(1800).build();
        when(authenticationService.refresh(null)).thenReturn(new AuthenticationResult(
                body, "refresh-token", remembered, LocalDateTime.now().plusDays(60)));

        String issued = configuredController.refresh(mock(HttpServletRequest.class))
                .getHeaders().getFirst("Set-Cookie");
        String cleared = configuredController.logout(mock(Principal.class), mock(HttpServletRequest.class))
                .getHeaders().getFirst("Set-Cookie");

        assertNotNull(issued);
        assertNotNull(cleared);
        assertTrue(issued.startsWith("project_session=refresh-token;"));
        assertTrue(cleared.startsWith("project_session=;"));
        for (String cookie : new String[]{issued, cleared}) {
            assertTrue(cookie.contains("Path=/api/v1/auth"));
            assertTrue(cookie.contains("HttpOnly"));
            assertEquals(secure, cookie.contains("Secure"));
            assertTrue(cookie.contains("SameSite=" + sameSite));
        }
        assertEquals(remembered, issued.contains("Max-Age="));
        assertTrue(cleared.contains("Max-Age=0"));
    }

    @Test
    void logoutRevokesAllUserSessionsAndClearsCookie() {
        Principal principal = mock(Principal.class);

        var response = controller.logout(principal, mock(HttpServletRequest.class));
        String cookie = response.getHeaders().getFirst("Set-Cookie");

        verify(authenticationService).logout(principal, null);
        assertEquals(204, response.getStatusCode().value());
        assertNotNull(cookie);
        assertTrue(cookie.contains("Max-Age=0"));
    }

    @Test
    void activationRequiresPostWithEmailAndSixDigitsInsteadOfUrlToken() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/auth/activate-account").param("token", "123456"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/auth/activate-account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\",\"code\":\"012345\"}"))
                .andExpect(status().isOk());
        verify(authenticationService).activateAccount(new ActivationRequest("person@example.com", "012345"));
    }

    @Test
    void malformedCodeIsRejectedBeforeCallingAuthenticationService() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/activate-account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\",\"code\":\"12345\"}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(authenticationService);
    }

    @Test
    void rateLimitResponseIncludesRetryAfterAndDoesNotIssueCode() throws Exception {
        doThrow(new AuthRateLimitException(60)).when(limits)
                .checkIp(AuthRateLimitService.Operation.REQUEST_CODE, "127.0.0.1");
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/resend-activation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        org.mockito.Mockito.verifyNoInteractions(authenticationService);
    }

    @Test
    void malformedJsonGetsSafeBadRequestWithoutExposingInput() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/activate-account").contentType(MediaType.APPLICATION_JSON)
                .content("{broken-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request body."));
        org.mockito.Mockito.verifyNoInteractions(authenticationService);
    }
}
