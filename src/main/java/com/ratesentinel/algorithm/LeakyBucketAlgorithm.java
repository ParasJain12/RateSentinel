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
public class LeakyBucketAlgorithm implements RateLimitAlgorithm {

    private final RedisCircuitBreakerService circuitBreakerService;
    private String luaScript;

    private String getLuaScript() {
        if (luaScript == null) {
            try {
                ClassPathResource resource =
                        new ClassPathResource("scripts/leaky_bucket.lua");
                luaScript = StreamUtils.copyToString(
                        resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to load leaky_bucket.lua", e);
            }
        }
        return luaScript;
    }

    @Override
    public RateLimitResult isAllowed(String identifier, RateLimitRule rule) {
        String redisKey = buildKey(identifier, rule);
        long nowMillis = Instant.now().toEpochMilli();

        // Leak rate = requests per second
        // e.g. limit=60, window=60s → leakRate=1 per second
        double leakRate = (double) rule.getLimitCount()
                / rule.getWindowSeconds();

        try {
            List<Long> result = circuitBreakerService.executeScript(
                    getLuaScript(),
                    RScript.ReturnType.MULTI,
                    Collections.singletonList(redisKey),
                    (long) rule.getLimitCount(),
                    (long) Math.max(1, leakRate),
                    nowMillis
            );

            boolean allowed = result.get(0) == 1L;
            int remaining = result.get(1).intValue();
            long retryAfter = result.get(2);
            long resetTime = Instant.now().getEpochSecond()
                    + rule.getWindowSeconds();

            if (allowed) {
                log.debug("ALLOW - LeakyBucket - Key: {}, Remaining: {}",
                        redisKey, remaining);
                return RateLimitResult.allowed(
                        remaining, rule.getLimitCount(),
                        resetTime, getAlgorithmName());
            } else {
                log.debug("BLOCK - LeakyBucket - Key: {}, RetryAfter: {}s",
                        redisKey, retryAfter);
                return RateLimitResult.blocked(
                        rule.getLimitCount(), resetTime,
                        retryAfter, getAlgorithmName());
            }

        } catch (Exception e) {
            log.error("Error in LeakyBucketAlgorithm: {}", e.getMessage());
            return RateLimitResult.allowed(
                    -1, rule.getLimitCount(), 0, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() {
        return "LEAKY_BUCKET";
    }

    private String buildKey(String identifier, RateLimitRule rule) {
        return String.format("rate:leaky:%s:%s:%s",
                identifier,
                rule.getEndpointPattern(),
                rule.getHttpMethod());
    }

}