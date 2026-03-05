package com.ratesentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratesentinel.dto.RateLimitRuleDTO;
import com.ratesentinel.model.RateLimitRule;
import com.ratesentinel.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

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
    private final RedisCircuitBreakerService circuitBreakerService;

    private static final String ALL_RULES_KEY = "ratesentinel:rules:all";
    private static final String RULE_KEY_PREFIX = "ratesentinel:rules:id:";
    private static final String ENDPOINT_RULE_KEY_PREFIX =
            "ratesentinel:rules:endpoint:";
    private static final long CACHE_TTL_MINUTES = 5;

    // ─── PUBLIC METHODS ────────────────────────────────────────

    public List<RateLimitRuleDTO> getAllRules() {
        // Use circuit breaker for Redis GET
        String cached = circuitBreakerService.getValue(ALL_RULES_KEY);

        if (cached != null) {
            log.debug("Cache HIT - getAllRules");
            return deserializeList(cached);
        }

        log.debug("Cache MISS - getAllRules, loading from MySQL");
        List<RateLimitRuleDTO> rules = ruleRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        // Use circuit breaker for Redis SET
        circuitBreakerService.setValue(
                ALL_RULES_KEY, serializeList(rules),
                CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return rules;
    }

    public Optional<RateLimitRuleDTO> getRuleById(Long id) {
        String cacheKey = RULE_KEY_PREFIX + id;
        String cached = circuitBreakerService.getValue(cacheKey);

        if (cached != null) {
            log.debug("Cache HIT - getRuleById: {}", id);
            return Optional.of(deserialize(cached));
        }

        log.debug("Cache MISS - getRuleById: {}", id);
        return ruleRepository.findById(id).map(rule -> {
            RateLimitRuleDTO dto = toDTO(rule);
            circuitBreakerService.setValue(
                    cacheKey, serialize(dto),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return dto;
        });
    }

    /**
     * THIS IS THE MOST IMPORTANT METHOD
     * Called on every single request
     * Now fully protected by circuit breaker
     */
    public Optional<RateLimitRule> findMatchingRuleCached(String endpoint,
                                                          String httpMethod) {
        String cacheKey = ENDPOINT_RULE_KEY_PREFIX + httpMethod + ":" + endpoint;

        // Circuit breaker protected GET
        String cached = circuitBreakerService.getValue(cacheKey);

        if (cached != null) {
            log.debug("Cache HIT - findMatchingRule: {} {}", httpMethod, endpoint);
            if (cached.equals("NULL")) return Optional.empty();
            return Optional.of(deserializeRule(cached));
        }

        log.debug("Cache MISS - findMatchingRule: {} {}", httpMethod, endpoint);

        // Load from MySQL
        Optional<RateLimitRule> rule =
                findMatchingRuleFromDB(endpoint, httpMethod);

        // Circuit breaker protected SET
        if (rule.isPresent()) {
            circuitBreakerService.setValue(
                    cacheKey, serializeRule(rule.get()),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } else {
            circuitBreakerService.setValue(
                    cacheKey, "NULL", 1, TimeUnit.MINUTES);
        }

        return rule;
    }

    public RateLimitRuleDTO createRule(RateLimitRuleDTO dto) {
        RateLimitRule rule = toEntity(dto);
        RateLimitRule saved = ruleRepository.save(rule);
        log.info("Created new rate limit rule: {}", saved.getRuleName());
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
            invalidateAllCaches();
            return toDTO(updated);
        });
    }

    public boolean toggleRule(Long id) {
        return ruleRepository.findById(id).map(rule -> {
            rule.setIsActive(!rule.getIsActive());
            ruleRepository.save(rule);
            log.info("Toggled rule {} active={}",
                    rule.getRuleName(), rule.getIsActive());
            invalidateAllCaches();
            return true;
        }).orElse(false);
    }

    public boolean deleteRule(Long id) {
        if (ruleRepository.existsById(id)) {
            ruleRepository.deleteById(id);
            log.info("Deleted rule id: {}", id);
            invalidateAllCaches();
            return true;
        }
        return false;
    }

    // ─── CACHE INVALIDATION ────────────────────────────────────

    private void invalidateAllCaches() {
        try {
            RKeys keys = redissonClient.getKeys();
            keys.deleteByPattern(RULE_KEY_PREFIX + "*");
            keys.deleteByPattern(ENDPOINT_RULE_KEY_PREFIX + "*");
            keys.delete(ALL_RULES_KEY);
            log.info("Rule cache invalidated successfully");
        } catch (Exception e) {
            // If Redis is down, cache invalidation fails silently
            // MySQL will serve fresh data anyway
            log.warn("Cache invalidation skipped - Redis unavailable: {}",
                    e.getMessage());
        }
    }

    // ─── DB QUERY ──────────────────────────────────────────────

    private Optional<RateLimitRule> findMatchingRuleFromDB(String endpoint,
                                                           String httpMethod) {
        Optional<RateLimitRule> exactMatch = ruleRepository
                .findByEndpointPatternAndHttpMethod(endpoint, httpMethod);

        if (exactMatch.isPresent() && exactMatch.get().getIsActive()) {
            return exactMatch;
        }

        List<RateLimitRule> activeRules = ruleRepository.findByIsActiveTrue();

        return activeRules.stream()
                .filter(rule -> matchesPattern(endpoint, rule.getEndpointPattern())
                        && rule.getHttpMethod().equals(httpMethod))
                .findFirst();
    }

    private boolean matchesPattern(String endpoint, String pattern) {
        if (pattern.equals(endpoint)) return true;
        if (pattern.endsWith("/**")) {
            return endpoint.startsWith(pattern.substring(0, pattern.length() - 3));
        }
        if (pattern.endsWith("/*")) {
            String base = pattern.substring(0, pattern.length() - 2);
            String remaining = endpoint.substring(
                    Math.min(base.length(), endpoint.length()));
            return endpoint.startsWith(base) && !remaining.contains("/");
        }
        return false;
    }

    // ─── SERIALIZATION ─────────────────────────────────────────

    private String serialize(RateLimitRuleDTO dto) {
        try { return objectMapper.writeValueAsString(dto); }
        catch (Exception e) { throw new RuntimeException("Serialize failed", e); }
    }

    private String serializeList(List<RateLimitRuleDTO> list) {
        try { return objectMapper.writeValueAsString(list); }
        catch (Exception e) { throw new RuntimeException("Serialize failed", e); }
    }

    private String serializeRule(RateLimitRule rule) {
        try { return objectMapper.writeValueAsString(rule); }
        catch (Exception e) { throw new RuntimeException("Serialize failed", e); }
    }

    private RateLimitRuleDTO deserialize(String json) {
        try { return objectMapper.readValue(json, RateLimitRuleDTO.class); }
        catch (Exception e) { throw new RuntimeException("Deserialize failed", e); }
    }

    private List<RateLimitRuleDTO> deserializeList(String json) {
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<RateLimitRuleDTO>>() {});
        }
        catch (Exception e) { throw new RuntimeException("Deserialize failed", e); }
    }

    private RateLimitRule deserializeRule(String json) {
        try { return objectMapper.readValue(json, RateLimitRule.class); }
        catch (Exception e) { throw new RuntimeException("Deserialize failed", e); }
    }

    // ─── MAPPERS ───────────────────────────────────────────────

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