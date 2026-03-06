package com.ratesentinel.service;

import com.ratesentinel.algorithm.AlgorithmFactory;
import com.ratesentinel.algorithm.RateLimitAlgorithm;
import com.ratesentinel.algorithm.RateLimitResult;
import com.ratesentinel.exception.RateLimitExceededException;
import com.ratesentinel.model.*;
import com.ratesentinel.repository.TierConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TierRateLimitService {

    private final TierConfigRepository tierConfigRepository;
    private final AlgorithmFactory algorithmFactory;
    private final RedisCircuitBreakerService circuitBreakerService;

    // Redis cache key prefix for tier configs
    private static final String TIER_CACHE_PREFIX = "ratesentinel:tier:";
    private static final long TIER_CACHE_TTL_MINUTES = 10;

    /**
     * MAIN METHOD
     * Called when a request has X-User-Tier header
     * Applies tier specific rate limit on top of endpoint rules
     */
    public void evaluateTierLimit(String identifier,
                                  String tierValue,
                                  String endpoint,
                                  String httpMethod) {

        UserTier userTier = UserTier.fromString(tierValue);
        log.debug("Tier check - Identifier: {}, Tier: {}", identifier, userTier);

        // INTERNAL tier is always allowed — no rate limiting
        if (userTier == UserTier.INTERNAL) {
            log.debug("INTERNAL tier - skipping rate limit for: {}", identifier);
            return;
        }

        // Get tier config from cache or database
        TierConfig tierConfig = getTierConfig(userTier);

        if (tierConfig == null) {
            log.warn("No tier config found for: {}, allowing request", userTier);
            return;
        }

        // Build a rule from tier config
        RateLimitRule tierRule = buildTierRule(tierConfig, endpoint, httpMethod);

        // Build tier specific Redis key
        // Includes tier name so FREE and PRO have separate counters
        String tierIdentifier = identifier + ":tier:" + userTier.name();

        // Evaluate rate limit
        RateLimitAlgorithm algorithm = algorithmFactory
                .getAlgorithm(tierConfig.getAlgorithm());
        RateLimitResult result = algorithm.isAllowed(tierIdentifier, tierRule);

        log.debug("Tier result - Tier: {}, Allowed: {}, Remaining: {}",
                userTier, result.isAllowed(), result.getRemainingRequests());

        if (!result.isAllowed()) {
            log.warn("TIER LIMIT EXCEEDED - Identifier: {}, Tier: {}, Endpoint: {}",
                    identifier, userTier, endpoint);
            throw new RateLimitExceededException(result, identifier, endpoint);
        }
    }

    /**
     * Get tier config from Redis cache first, then MySQL
     */
    private TierConfig getTierConfig(UserTier userTier) {
        String cacheKey = TIER_CACHE_PREFIX + userTier.name();

        // Try Redis cache first
        String cached = circuitBreakerService.getValue(cacheKey);
        if (cached != null) {
            log.debug("Tier cache HIT for: {}", userTier);
            return deserializeTierConfig(cached);
        }

        log.debug("Tier cache MISS for: {}", userTier);

        // Load from MySQL
        Optional<TierConfig> config = tierConfigRepository
                .findByTierNameAndIsActiveTrue(userTier.name());

        config.ifPresent(tc -> {
            // Cache tier config
            circuitBreakerService.setValue(
                    cacheKey,
                    serializeTierConfig(tc),
                    TIER_CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        });

        return config.orElse(null);
    }

    /**
     * Build RateLimitRule from TierConfig
     */
    private RateLimitRule buildTierRule(TierConfig config,
                                        String endpoint,
                                        String httpMethod) {
        return RateLimitRule.builder()
                .endpointPattern(endpoint)
                .httpMethod(httpMethod)
                .limitCount(config.getLimitCount())
                .windowSeconds(config.getWindowSeconds())
                .algorithm(config.getAlgorithm())
                .identifierType(IdentifierType.USER_ID)
                .isActive(true)
                .build();
    }

    /**
     * Invalidate tier cache when tier config changes
     */
    public void invalidateTierCache(String tierName) {
        try {
            circuitBreakerService.setValue(
                    TIER_CACHE_PREFIX + tierName, null, 0, TimeUnit.SECONDS);
            log.info("Tier cache invalidated for: {}", tierName);
        } catch (Exception e) {
            log.warn("Failed to invalidate tier cache: {}", e.getMessage());
        }
    }

    private String serializeTierConfig(TierConfig config) {
        return String.format("%s|%d|%d|%s",
                config.getTierName(),
                config.getLimitCount(),
                config.getWindowSeconds(),
                config.getAlgorithm().name()
        );
    }

    private TierConfig deserializeTierConfig(String cached) {
        try {
            String[] parts = cached.split("\\|");
            return TierConfig.builder()
                    .tierName(parts[0])
                    .limitCount(Integer.parseInt(parts[1]))
                    .windowSeconds(Integer.parseInt(parts[2]))
                    .algorithm(AlgorithmType.valueOf(parts[3]))
                    .isActive(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to deserialize tier config: {}", e.getMessage());
            return null;
        }
    }

}