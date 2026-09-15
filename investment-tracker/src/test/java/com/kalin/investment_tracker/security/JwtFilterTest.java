package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthSessionService authSessionService;

    private JwtService jwtService;
    private JwtFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        ));
        jwtService = new JwtService(properties);
        filter = new JwtFilter(jwtService, userRepository, authSessionService);
        user = User.builder()
                .id(7)
                .email("user@example.com")
                .enabled(true)
                .accountLocked(false)
                .roles(List.of())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeSessionAuthenticatesRequest() throws ServletException, IOException {
        String token = jwtService.generateAccessToken(user, "session-1");
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(authSessionService.isSessionActive("session-1", 7)).thenReturn(true);

        MockHttpServletResponse response = execute(token);

        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void revokedSessionRejectsOtherwiseValidToken() throws ServletException, IOException {
        String token = jwtService.generateAccessToken(user, "session-1");
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(authSessionService.isSessionActive("session-1", 7)).thenReturn(false);

        MockHttpServletResponse response = execute(token);

        assertEquals(401, response.getStatus());
    }

    @Test
    void modifiedTokenIsRejectedWithUnauthorized() throws ServletException, IOException {
        String token = jwtService.generateAccessToken(user, "session-1");
        char replacement = token.endsWith("a") ? 'b' : 'a';

        MockHttpServletResponse response = execute(
                token.substring(0, token.length() - 1) + replacement
        );

        assertEquals(401, response.getStatus());
    }

    private MockHttpServletResponse execute(String token) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
