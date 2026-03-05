package com.ratesentinel.interceptor;

import com.ratesentinel.algorithm.RateLimitResult;
import com.ratesentinel.dto.RateLimitDecisionDTO;
import com.ratesentinel.exception.RateLimitExceededException;
import com.ratesentinel.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Step 1 — Extract all identifiers from request
        String ipAddress = extractIpAddress(request);
        String userId = extractUserId(request);
        String apiKey = extractApiKey(request);
        String endpoint = request.getRequestURI();
        String httpMethod = request.getMethod();

        // Step 2 — Skip rate limiting for health check and swagger
        if (shouldSkip(endpoint)) {
            return true;
        }

        log.debug("Rate limit check - IP: {}, Endpoint: {} {}",
                ipAddress, httpMethod, endpoint);

        // Step 3 — Evaluate request
        // This calls RateLimitService which does everything
        RateLimitDecisionDTO decision = rateLimitService.evaluateRequest(
                ipAddress, userId, apiKey, endpoint, httpMethod);

        // Step 4 — Add rate limit headers to response
        // Even for allowed requests, we tell client how many requests they have left
        addRateLimitHeaders(response, decision);

        // Step 5 — If allowed, let request proceed
        return true;
    }

    /**
     * Extract real IP address
     * Handles proxies and load balancers correctly
     * X-Forwarded-For header contains real IP when behind proxy
     */
    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs — take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Extract user ID from request header or JWT token
     * For now reads from X-User-Id header
     * Later we can decode JWT token here
     */
    private String extractUserId(HttpServletRequest request) {
        return request.getHeader("X-User-Id");
    }

    /**
     * Extract API key from header
     * Standard practice is X-Api-Key header
     */
    private String extractApiKey(HttpServletRequest request) {
        return request.getHeader("X-Api-Key");
    }

    /**
     * Add standard rate limit headers to every response
     * This is what real APIs like GitHub and Stripe do
     */
    private void addRateLimitHeaders(HttpServletResponse response,
                                     RateLimitDecisionDTO decision) {
        if (decision.getTotalLimit() > 0) {
            response.setHeader("X-RateLimit-Limit",
                    String.valueOf(decision.getTotalLimit()));
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(decision.getRemainingRequests()));
            response.setHeader("X-RateLimit-Reset",
                    String.valueOf(decision.getResetTimeSeconds()));
            response.setHeader("X-RateLimit-Algorithm",
                    decision.getAlgorithmUsed());
        }
    }

    /**
     * Skip rate limiting for certain paths
     * Health checks, swagger, actuator should never be rate limited
     */
    private boolean shouldSkip(String endpoint) {
        return endpoint.startsWith("/actuator") ||
                endpoint.startsWith("/swagger-ui") ||
                endpoint.startsWith("/v3/api-docs") ||
                endpoint.startsWith("/favicon.ico") ||
                endpoint.equals("/health");
    }

}