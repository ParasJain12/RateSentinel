package com.ratesentinel.service;

import com.ratesentinel.algorithm.AlgorithmFactory;
import com.ratesentinel.algorithm.RateLimitAlgorithm;
import com.ratesentinel.algorithm.RateLimitResult;
import com.ratesentinel.dto.RateLimitDecisionDTO;
import com.ratesentinel.exception.RateLimitExceededException;
import com.ratesentinel.model.*;
import com.ratesentinel.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final AlgorithmFactory algorithmFactory;
    private final RateLimitRuleRepository ruleRepository;
    private final RateLimitLogRepository logRepository;
    private final WhitelistRepository whitelistRepository;
    private final BlacklistRepository blacklistRepository;
    private final AlertService alertService;
    private final RuleConfigService ruleConfigService;

    /**
     * MAIN METHOD — Called by interceptor for every incoming request
     * This is the entry point of the entire rate limiting system
     */
    public RateLimitDecisionDTO evaluateRequest(String ipAddress,
                                                String userId,
                                                String apiKey,
                                                String endpoint,
                                                String httpMethod) {

        // Step 1 — Check blacklist first
        // Blacklisted IPs are always blocked regardless of rate limits
        if (isBlacklisted(ipAddress)) {
            log.warn("BLACKLISTED IP blocked: {}", ipAddress);
            RateLimitDecisionDTO decision = buildBlockedDecision(
                    ipAddress, endpoint, httpMethod, "IP_BLACKLISTED", 0, 0);
            logDecisionAsync(ipAddress, endpoint, httpMethod, "BLOCK",
                    "BLACKLIST", 0, ipAddress, null);
            return decision;
        }

        // Step 2 — Check whitelist
        // Whitelisted IPs bypass all rate limiting
        if (isWhitelisted(ipAddress)) {
            log.debug("WHITELISTED IP allowed: {}", ipAddress);
            return buildAllowedDecision(ipAddress, endpoint,
                    httpMethod, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    0, "WHITELIST_BYPASS");
        }

        // Step 3 — Find matching rule for this endpoint
        Optional<RateLimitRule> ruleOpt = findMatchingRule(endpoint, httpMethod);

        if (ruleOpt.isEmpty()) {
            // No rule found — apply default behavior (allow)
            log.debug("No rule found for {} {}, allowing by default",
                    httpMethod, endpoint);
            return buildAllowedDecision(ipAddress, endpoint,
                    httpMethod, -1, -1, 0, "NO_RULE_FOUND");
        }

        RateLimitRule rule = ruleOpt.get();

        // Step 4 — Determine identifier based on rule config
        String identifier = resolveIdentifier(rule.getIdentifierType(),
                ipAddress, userId, apiKey);

        // Step 5 — Get correct algorithm and evaluate
        RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(rule.getAlgorithm());
        RateLimitResult result = algorithm.isAllowed(identifier, rule);

        // Step 6 — Log decision asynchronously (non-blocking)
        logDecisionAsync(
                identifier, endpoint, httpMethod,
                result.isAllowed() ? "ALLOW" : "BLOCK",
                result.getAlgorithmUsed(),
                result.getRemainingRequests(),
                ipAddress,
                null
        );

        // Step 7 — Check alert threshold if blocked
        if (!result.isAllowed()) {
            alertService.checkAndAlert(identifier, endpoint);
            throw new RateLimitExceededException(result, identifier, endpoint);
        }

        // Step 8 — Return allowed decision with headers info
        return RateLimitDecisionDTO.builder()
                .allowed(true)
                .identifier(identifier)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .remainingRequests(result.getRemainingRequests())
                .totalLimit(result.getTotalLimit())
                .resetTimeSeconds(result.getResetTimeSeconds())
                .retryAfterSeconds(0)
                .algorithmUsed(result.getAlgorithmUsed())
                .reason("ALLOWED")
                .build();
    }

    /**
     * Find the best matching rule for an endpoint
     * Exact match first, then wildcard match
     */
    private Optional<RateLimitRule> findMatchingRule(String endpoint,
                                                     String httpMethod) {
        // Now uses Redis cache instead of MySQL directly
        // MySQL only hit on first request or after cache invalidation
        return ruleConfigService.findMatchingRuleCached(endpoint, httpMethod);
    }

    /**
     * Simple pattern matching
     * Supports: /api/users/* matches /api/users/123
     * Supports: /api/** matches /api/users/123/orders
     */
    private boolean matchesPattern(String endpoint, String pattern) {
        if (pattern.equals(endpoint)) return true;

        if (pattern.endsWith("/**")) {
            String base = pattern.substring(0, pattern.length() - 3);
            return endpoint.startsWith(base);
        }

        if (pattern.endsWith("/*")) {
            String base = pattern.substring(0, pattern.length() - 2);
            String remaining = endpoint.substring(base.length());
            return endpoint.startsWith(base) && !remaining.contains("/");
        }

        return false;
    }

    /**
     * Resolve the identifier based on rule configuration
     * IP → use IP address
     * USER_ID → use authenticated user ID
     * API_KEY → use API key from header
     */
    private String resolveIdentifier(IdentifierType type,
                                     String ipAddress,
                                     String userId,
                                     String apiKey) {
        return switch (type) {
            case IP_ADDRESS -> ipAddress != null ? ipAddress : "unknown";
            case USER_ID -> userId != null ? userId : ipAddress;
            case API_KEY -> apiKey != null ? apiKey : ipAddress;
            case DEVICE_ID -> ipAddress; // fallback
        };
    }

    private boolean isBlacklisted(String ipAddress) {
        return ipAddress != null && blacklistRepository.existsByIpAddress(ipAddress);
    }

    private boolean isWhitelisted(String ipAddress) {
        return ipAddress != null && whitelistRepository.existsByIpAddress(ipAddress);
    }

    /**
     * Log decision asynchronously so it never slows down the main request
     */
    @Async
    public void logDecisionAsync(String identifier, String endpoint,
                                 String httpMethod, String decision,
                                 String algorithm, int remaining,
                                 String ipAddress, String country) {
        try {
            RateLimitLog log = RateLimitLog.builder()
                    .identifier(identifier)
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .decision(decision)
                    .algorithm(algorithm)
                    .remainingRequests(remaining)
                    .ipAddress(ipAddress)
                    .country(country)
                    .build();
            logRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save rate limit log: {}", e.getMessage());
        }
    }

    private RateLimitDecisionDTO buildBlockedDecision(String identifier,
                                                      String endpoint,
                                                      String httpMethod,
                                                      String reason,
                                                      long retryAfter,
                                                      long resetTime) {
        return RateLimitDecisionDTO.builder()
                .allowed(false)
                .identifier(identifier)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .remainingRequests(0)
                .totalLimit(0)
                .resetTimeSeconds(resetTime)
                .retryAfterSeconds(retryAfter)
                .algorithmUsed("BLACKLIST")
                .reason(reason)
                .build();
    }

    private RateLimitDecisionDTO buildAllowedDecision(String identifier,
                                                      String endpoint,
                                                      String httpMethod,
                                                      int remaining,
                                                      int total,
                                                      long resetTime,
                                                      String reason) {
        return RateLimitDecisionDTO.builder()
                .allowed(true)
                .identifier(identifier)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .remainingRequests(remaining)
                .totalLimit(total)
                .resetTimeSeconds(resetTime)
                .retryAfterSeconds(0)
                .algorithmUsed("NONE")
                .reason(reason)
                .build();
    }

}