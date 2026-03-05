package com.ratesentinel.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCircuitBreakerService {

    private final RedissonClient redissonClient;
    private final CircuitBreaker redisCircuitBreaker;

    /**
     * Execute a Lua script through circuit breaker
     * Used by all rate limiting algorithms
     */
    public List<Long> executeScript(String luaScript,
                                    RScript.ReturnType returnType,
                                    List<Object> keys,
                                    Object... args) {
        Supplier<List<Long>> redisCall = () -> {
            RScript script = redissonClient.getScript(LongCodec.INSTANCE);
            return script.eval(
                    RScript.Mode.READ_WRITE,
                    luaScript,
                    returnType,
                    keys,
                    args
            );
        };

        try {
            // Decorate with circuit breaker
            Supplier<List<Long>> decoratedCall = CircuitBreaker
                    .decorateSupplier(redisCircuitBreaker, redisCall);
            return decoratedCall.get();

        } catch (CallNotPermittedException e) {
            // Circuit is OPEN — Redis is down
            // Return fail-open result immediately without waiting
            log.warn("Circuit OPEN - Redis unavailable, failing open for script execution");
            return List.of(1L, -1L, 0L); // allow=1, remaining=-1, ttl=0
        } catch (Exception e) {
            log.error("Redis script execution failed: {}", e.getMessage());
            return List.of(1L, -1L, 0L); // fail open
        }
    }

    /**
     * Get value from Redis through circuit breaker
     */
    public String getValue(String key) {
        Supplier<String> redisCall = () -> {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.get();
        };

        try {
            Supplier<String> decoratedCall = CircuitBreaker
                    .decorateSupplier(redisCircuitBreaker, redisCall);
            return decoratedCall.get();

        } catch (CallNotPermittedException e) {
            log.warn("Circuit OPEN - Redis unavailable for GET: {}", key);
            return null; // Cache miss fallback
        } catch (Exception e) {
            log.error("Redis GET failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Set value in Redis through circuit breaker
     */
    public void setValue(String key, String value, long ttl, TimeUnit unit) {
        Supplier<Void> redisCall = () -> {
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(value, ttl, unit);
            return null;
        };

        try {
            Supplier<Void> decoratedCall = CircuitBreaker
                    .decorateSupplier(redisCircuitBreaker, redisCall);
            decoratedCall.get();

        } catch (CallNotPermittedException e) {
            log.warn("Circuit OPEN - Redis unavailable for SET: {}", key);
            // Non-critical — just skip caching
        } catch (Exception e) {
            log.error("Redis SET failed for key {}: {}", key, e.getMessage());
        }
    }

    /**
     * Get current circuit breaker state for dashboard
     */
    public String getCircuitBreakerState() {
        return redisCircuitBreaker.getState().name();
    }

    /**
     * Get circuit breaker metrics for dashboard
     */
    public CircuitBreakerMetrics getMetrics() {
        CircuitBreaker.Metrics metrics = redisCircuitBreaker.getMetrics();
        return new CircuitBreakerMetrics(
                redisCircuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfNotPermittedCalls()
        );
    }

    // Simple metrics record
    public record CircuitBreakerMetrics(
            String state,
            float failureRate,
            int successfulCalls,
            int failedCalls,
            long notPermittedCalls
    ) {}

}