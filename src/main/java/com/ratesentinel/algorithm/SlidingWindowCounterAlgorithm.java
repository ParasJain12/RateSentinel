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
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlidingWindowCounterAlgorithm implements RateLimitAlgorithm {

    private final RedisCircuitBreakerService circuitBreakerService;
    private String luaScript;

    private String getLuaScript() {
        if (luaScript == null) {
            try {
                ClassPathResource resource =
                        new ClassPathResource("scripts/sliding_window_counter.lua");
                luaScript = StreamUtils.copyToString(
                        resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to load sliding_window_counter.lua", e);
            }
        }
        return luaScript;
    }

    @Override
    public RateLimitResult isAllowed(String identifier, RateLimitRule rule) {
        long nowSeconds = Instant.now().getEpochSecond();

        // Calculate current window start
        // e.g. for 60s window at time 130 → window started at 120
        long windowStart = (nowSeconds / rule.getWindowSeconds())
                * rule.getWindowSeconds();

        // Current window key
        String currentKey = buildKey(identifier, rule, windowStart);

        // Previous window key
        String previousKey = buildKey(identifier, rule,
                windowStart - rule.getWindowSeconds());

        try {
            List<Long> result = circuitBreakerService.executeScript(
                    getLuaScript(),
                    RScript.ReturnType.MULTI,
                    Arrays.asList(currentKey, previousKey),
                    (long) rule.getLimitCount(),
                    (long) rule.getWindowSeconds(),
                    nowSeconds,
                    windowStart
            );

            boolean allowed = result.get(0) == 1L;
            int remaining = result.get(1).intValue();
            long retryAfter = result.get(2);
            long resetTime = windowStart + rule.getWindowSeconds();

            if (allowed) {
                log.debug("ALLOW - SlidingWindowCounter - Key: {}, Remaining: {}",
                        currentKey, remaining);
                return RateLimitResult.allowed(
                        remaining, rule.getLimitCount(),
                        resetTime, getAlgorithmName());
            } else {
                log.debug("BLOCK - SlidingWindowCounter - RetryAfter: {}s",
                        retryAfter);
                return RateLimitResult.blocked(
                        rule.getLimitCount(), resetTime,
                        retryAfter, getAlgorithmName());
            }

        } catch (Exception e) {
            log.error("Error in SlidingWindowCounterAlgorithm: {}",
                    e.getMessage());
            return RateLimitResult.allowed(
                    -1, rule.getLimitCount(), 0, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() {
        return "SLIDING_WINDOW_COUNTER";
    }

    private String buildKey(String identifier, RateLimitRule rule,
                            long windowStart) {
        return String.format("rate:swc:%s:%s:%s:%d",
                identifier,
                rule.getEndpointPattern(),
                rule.getHttpMethod(),
                windowStart);
    }

}