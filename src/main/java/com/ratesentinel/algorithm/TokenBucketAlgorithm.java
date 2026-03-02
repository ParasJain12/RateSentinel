package com.ratesentinel.algorithm;

import com.ratesentinel.model.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
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
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final RedissonClient redissonClient;
    private String luaScript;

    private String getLuaScript() {
        if (luaScript == null) {
            try {
                ClassPathResource resource =
                        new ClassPathResource("scripts/token_bucket.lua");
                luaScript = StreamUtils.copyToString(
                        resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load token_bucket.lua", e);
            }
        }
        return luaScript;
    }

    @Override
    public RateLimitResult isAllowed(String identifier, RateLimitRule rule) {

        String redisKey = buildKey(identifier, rule);
        long nowSeconds = Instant.now().getEpochSecond();

        // Refill rate = limit / window
        // e.g. 100 requests per 60 seconds = 1.67 tokens per second
        double refillRate = (double) rule.getLimitCount() / rule.getWindowSeconds();

        try {
            RScript script = redissonClient.getScript(LongCodec.INSTANCE);

            List<Long> result = script.eval(
                    RScript.Mode.READ_WRITE,
                    getLuaScript(),
                    RScript.ReturnType.MULTI,
                    Collections.singletonList(redisKey),
                    (long) rule.getLimitCount(),
                    (long) refillRate,
                    nowSeconds
            );

            boolean allowed = result.get(0) == 1L;
            int remaining = result.get(1).intValue();
            long retryAfter = result.get(2);
            long resetTime = nowSeconds + rule.getWindowSeconds();

            if (allowed) {
                log.debug("ALLOW - TokenBucket - Key: {}, Remaining tokens: {}",
                        redisKey, remaining);
                return RateLimitResult.allowed(
                        remaining, rule.getLimitCount(), resetTime, getAlgorithmName());
            } else {
                log.debug("BLOCK - TokenBucket - Key: {}, RetryAfter: {}s",
                        redisKey, retryAfter);
                return RateLimitResult.blocked(
                        rule.getLimitCount(), resetTime, retryAfter, getAlgorithmName());
            }

        } catch (Exception e) {
            log.error("Redis error in TokenBucketAlgorithm: {}", e.getMessage());
            return RateLimitResult.allowed(
                    -1, rule.getLimitCount(), 0, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() {
        return "TOKEN_BUCKET";
    }

    private String buildKey(String identifier, RateLimitRule rule) {
        return String.format("rate:token:%s:%s:%s",
                identifier,
                rule.getEndpointPattern(),
                rule.getHttpMethod());
    }

}