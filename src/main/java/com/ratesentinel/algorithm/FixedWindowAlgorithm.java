package com.ratesentinel.algorithm;

import com.ratesentinel.model.RateLimitRule;
import com.ratesentinel.service.RedisCircuitBreakerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private final RedisCircuitBreakerService circuitBreakerService;
    private String luaScript;

    private String getLuaScript() {
        if (luaScript == null) {
            try {
                ClassPathResource resource =
                        new ClassPathResource("scripts/fixed_window.lua");
                luaScript = StreamUtils.copyToString(
                        resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load fixed_window.lua", e);
            }
        }
        return luaScript;
    }

    @Override
    public RateLimitResult isAllowed(String identifier, RateLimitRule rule) {
        String redisKey = buildKey(identifier, rule);

        List<Long> result = circuitBreakerService.executeScript(
                getLuaScript(),
                RScript.ReturnType.MULTI,
                Collections.singletonList(redisKey),
                (long) rule.getLimitCount(),
                (long) rule.getWindowSeconds()
        );

        boolean allowed = result.get(0) == 1L;
        int remaining = result.get(1).intValue();
        long ttl = result.get(2);
        long resetTime = Instant.now().getEpochSecond() + ttl;

        if (allowed) {
            log.debug("ALLOW - FixedWindow - Key: {}, Remaining: {}",
                    redisKey, remaining);
            return RateLimitResult.allowed(
                    remaining, rule.getLimitCount(), resetTime, getAlgorithmName());
        } else {
            log.debug("BLOCK - FixedWindow - Key: {}, RetryAfter: {}s",
                    redisKey, ttl);
            return RateLimitResult.blocked(
                    rule.getLimitCount(), resetTime, ttl, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() { return "FIXED_WINDOW"; }

    private String buildKey(String identifier, RateLimitRule rule) {
        return String.format("rate:fixed:%s:%s:%s",
                identifier, rule.getEndpointPattern(), rule.getHttpMethod());
    }
}