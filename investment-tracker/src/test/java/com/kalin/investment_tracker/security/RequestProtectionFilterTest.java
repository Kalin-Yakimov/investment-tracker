package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.validation.ApiRequestBudget;
import com.kalin.investment_tracker.validation.ResourceLimitsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class RequestProtectionFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void declaredAndUndeclaredOversizedBodiesStopBeforeAuthenticationOrControllerWork() throws Exception {
        var limits = new ResourceLimitsProperties(); limits.setRequestBodyBytes(1024);
        var filter = new RequestProtectionFilter(limits, new ApiRequestBudget(limits));
        for (boolean declared : List.of(true, false)) {
            var request = new MockHttpServletRequest("POST", "/item") {
                @Override public long getContentLengthLong() { return declared ? 1025 : -1; }
            };
            request.setContent(new byte[1025]);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("Oversized request reached downstream"));
            assertEquals(413, response.getStatus());
            assertTrue(response.getContentAsString().contains("too large"));
        }
    }
    @Test void exactLimitAndUtf8BytesRemainIntactForJsonParsing() throws Exception {
        var limits = new ResourceLimitsProperties(); limits.setRequestBodyBytes(1024);
        var filter = new RequestProtectionFilter(limits, new ApiRequestBudget(limits));
        var request = new MockHttpServletRequest("POST", "/api/contact");
        byte[] body = "ж".repeat(512).getBytes(StandardCharsets.UTF_8); request.setContent(body);
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertArrayEquals(body, req.getInputStream().readAllBytes()); reached.set(true);
        });
        assertTrue(reached.get());
    }
    @Test void spoofedForwardingHeadersDoNotChangeTheDirectPeerBudget() throws Exception {
        var limits = new ResourceLimitsProperties(); limits.setRequestsPerIpMinute(1);
        var filter = new RequestProtectionFilter(limits, new ApiRequestBudget(limits));
        var first = new MockHttpServletRequest("GET", "/api/items"); first.setRemoteAddr("192.0.2.1");
        filter.doFilter(first, new MockHttpServletResponse(), (req, res) -> {});
        var next = new MockHttpServletRequest("GET", "/api/items"); next.setRemoteAddr("192.0.2.1");
        next.addHeader("X-Forwarded-For", "198.51.100.50");
        var response = new MockHttpServletResponse();
        filter.doFilter(next, response, (req, res) -> fail("Spoofing reset the budget"));
        assertEquals(429, response.getStatus()); assertEquals("60", response.getHeader("Retry-After"));
    }
    @Test void accountLimitIsSharedAcrossTokensAndIpsButLogoutRemainsAvailable() throws Exception {
        var limits = new ResourceLimitsProperties(); limits.setInventoriesPerUserMinute(1);
        var filter = new AuthenticatedRequestBudgetFilter(limits, new ApiRequestBudget(limits));
        User user = new User(); user.setId(7);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/inventory/calculate"), new MockHttpServletResponse(), (req, res) -> {});
        var response = new MockHttpServletResponse();
        var next = new MockHttpServletRequest("POST", "/api/v1/inventory/calculate"); next.setRemoteAddr("198.51.100.2");
        filter.doFilter(next, response, (req, res) -> fail("Account inventory budget was bypassed"));
        assertEquals(429, response.getStatus());
        var logout = new MockHttpServletRequest("POST", "/api/v1/auth/logout"); logout.setServletPath("/api/v1/auth/logout");
        var reached = new AtomicBoolean();
        filter.doFilter(logout, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertTrue(reached.get());
    }
}
