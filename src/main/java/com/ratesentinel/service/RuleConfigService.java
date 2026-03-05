package com.ratesentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratesentinel.dto.RateLimitRuleDTO;
import com.ratesentinel.model.RateLimitRule;
import com.ratesentinel.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleConfigService {

    private final RateLimitRuleRepository ruleRepository;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // Redis key constants
    private static final String ALL_RULES_KEY = "ratesentinel:rules:all";
    private static final String RULE_KEY_PREFIX = "ratesentinel:rules:id:";
    private static final String ENDPOINT_RULE_KEY_PREFIX = "ratesentinel:rules:endpoint:";

    // Cache TTL — 5 minutes
    // Rules don't change often so 5 minutes is safe
    private static final long CACHE_TTL_MINUTES = 5;

    // ─── PUBLIC METHODS ────────────────────────────────────────────

    public List<RateLimitRuleDTO> getAllRules() {
        // Try cache first
        RBucket<String> bucket = redissonClient.getBucket(ALL_RULES_KEY);
        String cached = bucket.get();

        if (cached != null) {
            log.debug("Cache HIT - getAllRules");
            return deserializeList(cached);
        }

        log.debug("Cache MISS - getAllRules, loading from MySQL");
        List<RateLimitRuleDTO> rules = ruleRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        // Store in cache
        bucket.set(serializeList(rules), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return rules;
    }

    public Optional<RateLimitRuleDTO> getRuleById(Long id) {
        String cacheKey = RULE_KEY_PREFIX + id;
        RBucket<String> bucket = redissonClient.getBucket(cacheKey);
        String cached = bucket.get();

        if (cached != null) {
            log.debug("Cache HIT - getRuleById: {}", id);
            return Optional.of(deserialize(cached));
        }

        log.debug("Cache MISS - getRuleById: {}", id);
        return ruleRepository.findById(id).map(rule -> {
            RateLimitRuleDTO dto = toDTO(rule);
            bucket.set(serialize(dto), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return dto;
        });
    }

    /**
     * THIS IS THE MOST IMPORTANT CACHE METHOD
     * Called by RateLimitService on every request
     * Must be extremely fast
     */
    public Optional<RateLimitRule> findMatchingRuleCached(String endpoint,
                                                          String httpMethod) {
        String cacheKey = ENDPOINT_RULE_KEY_PREFIX + httpMethod + ":" + endpoint;
        RBucket<String> bucket = redissonClient.getBucket(cacheKey);
        String cached = bucket.get();

        if (cached != null) {
            log.debug("Cache HIT - findMatchingRule: {} {}", httpMethod, endpoint);
            if (cached.equals("NULL")) return Optional.empty();
            return Optional.of(deserializeRule(cached));
        }

        log.debug("Cache MISS - findMatchingRule: {} {}", httpMethod, endpoint);

        // Load from MySQL
        Optional<RateLimitRule> rule = findMatchingRuleFromDB(endpoint, httpMethod);

        // Cache the result — even if empty (cache NULL to prevent DB hammering)
        if (rule.isPresent()) {
            bucket.set(serializeRule(rule.get()),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } else {
            // Cache NULL result for 1 minute to prevent repeated DB misses
            bucket.set("NULL", 1, TimeUnit.MINUTES);
        }

        return rule;
    }

    public RateLimitRuleDTO createRule(RateLimitRuleDTO dto) {
        RateLimitRule rule = toEntity(dto);
        RateLimitRule saved = ruleRepository.save(rule);
        log.info("Created new rate limit rule: {}", saved.getRuleName());

        // Invalidate all rule caches
        invalidateAllCaches();

        return toDTO(saved);
    }

    public Optional<RateLimitRuleDTO> updateRule(Long id, RateLimitRuleDTO dto) {
        return ruleRepository.findById(id).map(existing -> {
            existing.setRuleName(dto.getRuleName());
            existing.setEndpointPattern(dto.getEndpointPattern());
            existing.setHttpMethod(dto.getHttpMethod());
            existing.setLimitCount(dto.getLimitCount());
            existing.setWindowSeconds(dto.getWindowSeconds());
            existing.setAlgorithm(dto.getAlgorithm());
            existing.setIdentifierType(dto.getIdentifierType());
            existing.setUserTier(dto.getUserTier());
            existing.setIsActive(dto.getIsActive());
            RateLimitRule updated = ruleRepository.save(existing);
            log.info("Updated rate limit rule: {}", updated.getRuleName());

            // Invalidate all caches when rule changes
            invalidateAllCaches();

            return toDTO(updated);
        });
    }

    public boolean toggleRule(Long id) {
        return ruleRepository.findById(id).map(rule -> {
            rule.setIsActive(!rule.getIsActive());
            ruleRepository.save(rule);
            log.info("Toggled rule {} to {}", rule.getRuleName(), rule.getIsActive());

            // Invalidate caches immediately
            invalidateAllCaches();

            return true;
        }).orElse(false);
    }

    public boolean deleteRule(Long id) {
        if (ruleRepository.existsById(id)) {
            ruleRepository.deleteById(id);
            log.info("Deleted rule with id: {}", id);

            // Invalidate caches
            invalidateAllCaches();

            return true;
        }
        return false;
    }

    // ─── CACHE INVALIDATION ────────────────────────────────────────

    /**
     * Delete all rule-related cache keys from Redis
     * Called whenever any rule is created, updated, toggled, or deleted
     */
    private void invalidateAllCaches() {
        try {
            RKeys keys = redissonClient.getKeys();

            // Delete all keys matching our rule cache pattern
            keys.deleteByPattern(RULE_KEY_PREFIX + "*");
            keys.deleteByPattern(ENDPOINT_RULE_KEY_PREFIX + "*");
            keys.delete(ALL_RULES_KEY);

            log.info("Rule cache invalidated successfully");
        } catch (Exception e) {
            log.error("Failed to invalidate rule cache: {}", e.getMessage());
        }
    }

    // ─── PRIVATE HELPERS ───────────────────────────────────────────

    private Optional<RateLimitRule> findMatchingRuleFromDB(String endpoint,
                                                           String httpMethod) {
        // Exact match first
        Optional<RateLimitRule> exactMatch = ruleRepository
                .findByEndpointPatternAndHttpMethod(endpoint, httpMethod);

        if (exactMatch.isPresent() && exactMatch.get().getIsActive()) {
            return exactMatch;
        }

        // Wildcard match
        List<RateLimitRule> activeRules = ruleRepository.findByIsActiveTrue();

        return activeRules.stream()
                .filter(rule -> matchesPattern(endpoint, rule.getEndpointPattern())
                        && rule.getHttpMethod().equals(httpMethod))
                .findFirst();
    }

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

    // ─── SERIALIZATION ─────────────────────────────────────────────

    private String serialize(RateLimitRuleDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize rule DTO", e);
        }
    }

    private String serializeList(List<RateLimitRuleDTO> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize rule list", e);
        }
    }

    private String serializeRule(RateLimitRule rule) {
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize rule", e);
        }
    }

    private RateLimitRuleDTO deserialize(String json) {
        try {
            return objectMapper.readValue(json, RateLimitRuleDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize rule DTO", e);
        }
    }

    private List<RateLimitRuleDTO> deserializeList(String json) {
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<RateLimitRuleDTO>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize rule list", e);
        }
    }

    private RateLimitRule deserializeRule(String json) {
        try {
            return objectMapper.readValue(json, RateLimitRule.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize rule", e);
        }
    }

    // ─── MAPPERS ───────────────────────────────────────────────────

    private RateLimitRuleDTO toDTO(RateLimitRule rule) {
        return RateLimitRuleDTO.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .endpointPattern(rule.getEndpointPattern())
                .httpMethod(rule.getHttpMethod())
                .limitCount(rule.getLimitCount())
                .windowSeconds(rule.getWindowSeconds())
                .algorithm(rule.getAlgorithm())
                .identifierType(rule.getIdentifierType())
                .userTier(rule.getUserTier())
                .isActive(rule.getIsActive())
                .build();
    }

    private RateLimitRule toEntity(RateLimitRuleDTO dto) {
        return RateLimitRule.builder()
                .ruleName(dto.getRuleName())
                .endpointPattern(dto.getEndpointPattern())
                .httpMethod(dto.getHttpMethod())
                .limitCount(dto.getLimitCount())
                .windowSeconds(dto.getWindowSeconds())
                .algorithm(dto.getAlgorithm())
                .identifierType(dto.getIdentifierType())
                .userTier(dto.getUserTier())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
    }

}