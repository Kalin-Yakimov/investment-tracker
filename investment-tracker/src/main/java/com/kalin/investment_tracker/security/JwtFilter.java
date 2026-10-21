package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Service
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthSessionService authSessionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader(AUTHORIZATION);
        final String jwt;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            Claims claims = jwtService.parseAndValidate(jwt);
            Integer userId = jwtService.extractUserId(claims);
            String sessionId = jwtService.extractSessionId(claims);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null
                    || !user.isEnabled()
                    || !user.isAccountNonLocked()
                    || !user.isAccountNonExpired()
                    || !user.isCredentialsNonExpired()
                    || !jwtService.isAccessTokenForUser(claims, user)
                    || !authSessionService.isSessionActive(sessionId, userId)) {
                if (!isLogout(request)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked access token");
                    return;
                }
            } else {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                request.setAttribute("authSessionId", sessionId);
            }
        } catch (JwtException | IllegalArgumentException e) {
            if (!isLogout(request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired access token");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLogout(HttpServletRequest request) {
        return CookieAuthenticationFilter.LOGOUT.matches(request);
    }
}
