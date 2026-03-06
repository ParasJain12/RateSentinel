package com.ratesentinel.interceptor;

import com.ratesentinel.dto.RateLimitDecisionDTO;
import com.ratesentinel.service.RateLimitService;
import com.ratesentinel.service.TierRateLimitService;
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
    private final TierRateLimitService tierRateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String ipAddress = extractIpAddress(request);
        String userId = extractUserId(request);
        String apiKey = extractApiKey(request);
        String userTier = extractUserTier(request);
        String endpoint = request.getRequestURI();
        String httpMethod = request.getMethod();

        if (shouldSkip(endpoint)) {
            return true;
        }

        log.debug("Rate limit check - IP: {}, Tier: {}, Endpoint: {} {}",
                ipAddress, userTier, httpMethod, endpoint);

        // Step 1 — Apply endpoint based rate limit (existing logic)
        RateLimitDecisionDTO decision = rateLimitService.evaluateRequest(
                ipAddress, userId, apiKey, endpoint, httpMethod);

        // Step 2 — Apply tier based rate limit if tier header present
        // This runs AFTER endpoint check so both limits apply
        if (userTier != null && !userTier.isEmpty()) {
            String identifier = userId != null ? userId : ipAddress;
            tierRateLimitService.evaluateTierLimit(
                    identifier, userTier, endpoint, httpMethod);
        }

        // Step 3 — Add headers
        addRateLimitHeaders(response, decision);

        return true;
    }

    /**
     * Extract user tier from request header
     * Header: X-User-Tier: FREE / PRO / ENTERPRISE / INTERNAL
     */
    private String extractUserTier(HttpServletRequest request) {
        return request.getHeader("X-User-Tier");
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserId(HttpServletRequest request) {
        return request.getHeader("X-User-Id");
    }

    private String extractApiKey(HttpServletRequest request) {
        return request.getHeader("X-Api-Key");
    }

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

    private boolean shouldSkip(String endpoint) {
        return endpoint.startsWith("/actuator") ||
                endpoint.startsWith("/swagger-ui") ||
                endpoint.startsWith("/v3/api-docs") ||
                endpoint.startsWith("/favicon.ico") ||
                endpoint.equals("/health");
    }

}