package com.kalin.investment_tracker.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

    // Shared with the development OpenAPI description; changes still require security tests.
    public static final List<String> PUBLIC_AUTH_PATHS = List.of(
            "/api/v1/auth/register", "/api/v1/auth/authenticate", "/api/v1/auth/activate-account",
            "/api/v1/auth/resend-activation", "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password", "/api/v1/auth/refresh");
    private static final RequestMatcher DOCUMENTATION = new OrRequestMatcher(
            List.of("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**")
                    .stream().map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(path)).toList());

    private final JwtFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final com.kalin.investment_tracker.validation.ResourceLimitsProperties resourceLimits;
    private final com.kalin.investment_tracker.validation.ApiRequestBudget requestBudgets;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean development = environment.acceptsProfiles(Profiles.of("dev & !prod"));
        RequestMatcher localDocumentationRead = request -> development
                && Set.of("GET", "HEAD").contains(request.getMethod())
                && Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())
                && DOCUMENTATION.matches(request);
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req -> req
                        .requestMatchers(localDocumentationRead).permitAll()
                        // Deny outside local dev even if springdoc properties are accidentally enabled.
                        .requestMatchers(DOCUMENTATION).denyAll()
                        .requestMatchers(CookieAuthenticationFilter.LOGOUT).permitAll()
                        // Public Authentication Routes (Register, Login, Activation, Password Reset)
                        .requestMatchers(PUBLIC_AUTH_PATHS.toArray(String[]::new)).permitAll()
                        // All Website Features (Portfolio, Price Tracker, Search, Inventory Calculator) require login
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(401, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(403, "Access denied"))
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new CookieAuthenticationFilter(), JwtFilter.class)
                .addFilterBefore(new RequestProtectionFilter(resourceLimits, requestBudgets), CookieAuthenticationFilter.class)
                .addFilterAfter(new AuthenticatedRequestBudgetFilter(resourceLimits, requestBudgets), JwtFilter.class);

        return http.build();
    }
}
