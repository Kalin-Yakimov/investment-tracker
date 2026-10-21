package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.validation.ApiRequestBudget;
import com.kalin.investment_tracker.validation.ResourceLimitException;
import com.kalin.investment_tracker.validation.ResourceLimitsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Account-wide budgets cannot be reset by obtaining another token or changing the source IP. */
public class AuthenticatedRequestBudgetFilter extends OncePerRequestFilter {
    private final ResourceLimitsProperties limits;
    private final ApiRequestBudget budgets;
    public AuthenticatedRequestBudgetFilter(ResourceLimitsProperties limits, ApiRequestBudget budgets) {
        this.limits = limits; this.budgets = budgets;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User user
                && !CookieAuthenticationFilter.LOGOUT.matches(request)) {
            try {
                String account = "user:" + user.getId();
                budgets.consume(account + ":all", limits.getRequestsPerUserMinute());
                if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod()) && !"OPTIONS".equals(request.getMethod())) {
                    budgets.consume(account + ":writes", limits.getWritesPerUserMinute());
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    if (path.equals("/api/v1/inventory/calculate")) budgets.consume(account + ":inventory", limits.getInventoriesPerUserMinute());
                    if (path.equals("/api/prices/update") || path.equals("/api/prices/validate")) budgets.consume(account + ":prices", limits.getPriceUpdatesPerUserMinute());
                    if (path.equals("/api/contact")) budgets.consume(account + ":contact", limits.getContactRequestsPerUserMinute());
                    if (path.startsWith("/api/admin/system/")) budgets.consume(account + ":maintenance", limits.getMaintenancePerUserMinute());
                }
            } catch (ResourceLimitException limited) { RequestProtectionFilter.writeLimit(response, limited); return; }
        }
        chain.doFilter(request, response);
    }
}
