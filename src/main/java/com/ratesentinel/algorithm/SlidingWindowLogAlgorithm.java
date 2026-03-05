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
public class SlidingWindowLogAlgorithm implements RateLimitAlgorithm {

    private final RedisCircuitBreakerService circuitBreakerService;
    private String luaScript;

    private String getLuaScript() {
        if (luaScript == null) {
            try {
                ClassPathResource resource =
                        new ClassPathResource("scripts/sliding_window.lua");
                luaScript = StreamUtils.copyToString(
                        resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load sliding_window.lua", e);
            }
        }
        return luaScript;
    }

    @Override
    public RateLimitResult isAllowed(String identifier, RateLimitRule rule) {
        String redisKey = buildKey(identifier, rule);
        long nowMillis = Instant.now().toEpochMilli();

        List<Long> result = circuitBreakerService.executeScript(
                getLuaScript(),
                RScript.ReturnType.MULTI,
                Collections.singletonList(redisKey),
                (long) rule.getLimitCount(),
                (long) rule.getWindowSeconds(),
                nowMillis
        );

        boolean allowed = result.get(0) == 1L;
        int remaining = result.get(1).intValue();
        long retryAfter = result.get(2);
        long resetTime = Instant.now().getEpochSecond() + rule.getWindowSeconds();

        if (allowed) {
            log.debug("ALLOW - SlidingWindow - Key: {}, Remaining: {}",
                    redisKey, remaining);
            return RateLimitResult.allowed(
                    remaining, rule.getLimitCount(), resetTime, getAlgorithmName());
        } else {
            log.debug("BLOCK - SlidingWindow - Key: {}, RetryAfter: {}s",
                    redisKey, retryAfter);
            return RateLimitResult.blocked(
                    rule.getLimitCount(), resetTime, retryAfter, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() { return "SLIDING_WINDOW_LOG"; }

    private String buildKey(String identifier, RateLimitRule rule) {
        return String.format("rate:sliding:%s:%s:%s",
                identifier, rule.getEndpointPattern(), rule.getHttpMethod());
    }
}