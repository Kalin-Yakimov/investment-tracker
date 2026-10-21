package com.kalin.investment_tracker.security;

import com.kalin.investment_tracker.validation.ApiRequestBudget;
import com.kalin.investment_tracker.validation.ResourceLimitException;
import com.kalin.investment_tracker.validation.ResourceLimitsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Before JWT/database work. Limits actual bytes even when Content-Length is absent. */
public class RequestProtectionFilter extends OncePerRequestFilter {
    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults().build();
    private final ResourceLimitsProperties limits;
    private final ApiRequestBudget budgets;
    public RequestProtectionFilter(ResourceLimitsProperties limits, ApiRequestBudget budgets) {
        this.limits = limits; this.budgets = budgets;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return "OPTIONS".equals(request.getMethod()); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try { budgets.consume("ip:" + request.getRemoteAddr(), limits.getRequestsPerIpMinute()); }
        catch (ResourceLimitException limited) { writeLimit(response, limited); return; }
        int maximum = limits.getRequestBodyBytes();
        if (request.getContentLengthLong() > maximum) { tooLarge(response); return; }
        byte[] bytes = request.getInputStream().readNBytes(maximum + 1);
        if (bytes.length > maximum) { tooLarge(response); return; }
        chain.doFilter(new BufferedRequest(request, bytes), response);
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        writeJson(response, 413, java.util.Map.of("error", "Request body is too large."));
    }

    static void writeLimit(HttpServletResponse response, ResourceLimitException limited) throws IOException {
        if (limited.getRetryAfterSeconds() > 0) response.setHeader("Retry-After", Long.toString(limited.getRetryAfterSeconds()));
        writeJson(response, limited.getStatus().value(), java.util.Map.of("error", limited.getMessage(),
                "retryAfterSeconds", limited.getRetryAfterSeconds()));
    }

    private static void writeJson(HttpServletResponse response, int status, java.util.Map<String, ?> body) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name()); response.getWriter().write(JSON.writeValueAsString(body));
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;
        BufferedRequest(HttpServletRequest request, byte[] bytes) { super(request); this.bytes = bytes; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new IllegalStateException("Synchronous JSON request only"); }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
