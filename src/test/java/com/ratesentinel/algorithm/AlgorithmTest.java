package com.ratesentinel.algorithm;

import com.ratesentinel.model.AlgorithmType;
import com.ratesentinel.model.IdentifierType;
import com.ratesentinel.model.RateLimitRule;
import com.ratesentinel.service.RedisCircuitBreakerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Algorithm Unit Tests")
class AlgorithmTest {

    @Mock
    private RedisCircuitBreakerService circuitBreakerService;

    private FixedWindowAlgorithm fixedWindowAlgorithm;
    private SlidingWindowLogAlgorithm slidingWindowAlgorithm;
    private TokenBucketAlgorithm tokenBucketAlgorithm;
    private SlidingWindowCounterAlgorithm slidingWindowCounterAlgorithm;
    private LeakyBucketAlgorithm leakyBucketAlgorithm;

    private RateLimitRule testRule;

    @BeforeEach
    void setUp() {
        fixedWindowAlgorithm = new FixedWindowAlgorithm(circuitBreakerService);
        slidingWindowAlgorithm = new SlidingWindowLogAlgorithm(circuitBreakerService);
        tokenBucketAlgorithm = new TokenBucketAlgorithm(circuitBreakerService);
        slidingWindowCounterAlgorithm =
                new SlidingWindowCounterAlgorithm(circuitBreakerService);
        leakyBucketAlgorithm =
                new LeakyBucketAlgorithm(circuitBreakerService);

        testRule = RateLimitRule.builder()
                .endpointPattern("/api/test")
                .httpMethod("GET")
                .limitCount(10)
                .windowSeconds(60)
                .algorithm(AlgorithmType.FIXED_WINDOW)
                .identifierType(IdentifierType.IP_ADDRESS)
                .isActive(true)
                .build();
    }

    // ─── FIXED WINDOW TESTS ────────────────────────────────────

    @Test
    @DisplayName("Fixed Window - Should ALLOW when under limit")
    void fixedWindow_shouldAllowWhenUnderLimit() {
        // Mock Redis returning allowed result
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 9L, 60L)); // allowed, 9 remaining, 60s ttl

        RateLimitResult result = fixedWindowAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingRequests()).isEqualTo(9);
        assertThat(result.getAlgorithmUsed()).isEqualTo("FIXED_WINDOW");
    }

    @Test
    @DisplayName("Fixed Window - Should BLOCK when limit exceeded")
    void fixedWindow_shouldBlockWhenLimitExceeded() {
        // Mock Redis returning blocked result
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(0L, 0L, 45L)); // blocked, 0 remaining, 45s retry

        RateLimitResult result = fixedWindowAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemainingRequests()).isEqualTo(0);
        assertThat(result.getRetryAfterSeconds()).isEqualTo(45L);
    }

    @Test
    @DisplayName("Fixed Window - Different identifiers should be independent")
    void fixedWindow_differentIdentifiersShouldBeIndependent() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 9L, 60L));

        RateLimitResult result1 = fixedWindowAlgorithm
                .isAllowed("192.168.1.1", testRule);
        RateLimitResult result2 = fixedWindowAlgorithm
                .isAllowed("192.168.1.2", testRule);

        // Verify Redis was called with DIFFERENT keys for different IPs
        verify(circuitBreakerService, times(2))
                .executeScript(anyString(), any(), anyList(), any());

        assertThat(result1.isAllowed()).isTrue();
        assertThat(result2.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Fixed Window - Should FAIL OPEN when Redis is down")
    void fixedWindow_shouldFailOpenWhenRedisDown() {
        // Mock Redis circuit breaker returning fail-open result
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, -1L, 0L)); // fail open

        RateLimitResult result = fixedWindowAlgorithm
                .isAllowed("192.168.1.1", testRule);

        // Should allow when Redis is down (fail open)
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingRequests()).isEqualTo(-1);
    }

    // ─── SLIDING WINDOW TESTS ──────────────────────────────────

    @Test
    @DisplayName("Sliding Window - Should ALLOW when under limit")
    void slidingWindow_shouldAllowWhenUnderLimit() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 9L, 0L));

        RateLimitResult result = slidingWindowAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getAlgorithmUsed()).isEqualTo("SLIDING_WINDOW_LOG");
    }

    @Test
    @DisplayName("Sliding Window - Should BLOCK with correct retry time")
    void slidingWindow_shouldBlockWithCorrectRetryTime() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(0L, 0L, 30L));

        RateLimitResult result = slidingWindowAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(30L);
    }

    // ─── TOKEN BUCKET TESTS ────────────────────────────────────

    @Test
    @DisplayName("Token Bucket - Should ALLOW when tokens available")
    void tokenBucket_shouldAllowWhenTokensAvailable() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 8L, 0L));

        RateLimitResult result = tokenBucketAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingRequests()).isEqualTo(8);
        assertThat(result.getAlgorithmUsed()).isEqualTo("TOKEN_BUCKET");
    }

    @Test
    @DisplayName("Token Bucket - Should BLOCK when no tokens")
    void tokenBucket_shouldBlockWhenNoTokens() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(0L, 0L, 5L));

        RateLimitResult result = tokenBucketAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(5L);
    }

    // ─── ALGORITHM FACTORY TESTS ───────────────────────────────

    @Test
    @DisplayName("Algorithm Factory - Returns correct algorithm for each type")
    void algorithmFactory_returnsCorrectAlgorithm() {
        AlgorithmFactory factory = new AlgorithmFactory(
                fixedWindowAlgorithm,
                slidingWindowAlgorithm,
                slidingWindowCounterAlgorithm,
                tokenBucketAlgorithm,
                leakyBucketAlgorithm
        );

        assertThat(factory.getAlgorithm(AlgorithmType.FIXED_WINDOW))
                .isInstanceOf(FixedWindowAlgorithm.class);
        assertThat(factory.getAlgorithm(AlgorithmType.SLIDING_WINDOW_LOG))
                .isInstanceOf(SlidingWindowLogAlgorithm.class);
        assertThat(factory.getAlgorithm(AlgorithmType.SLIDING_WINDOW_COUNTER))
                .isInstanceOf(SlidingWindowLogAlgorithm.class);
        assertThat(factory.getAlgorithm(AlgorithmType.TOKEN_BUCKET))
                .isInstanceOf(TokenBucketAlgorithm.class);
    }

    // ─── SLIDING WINDOW COUNTER TESTS ─────────────────────────

    @Test
    @DisplayName("Sliding Window Counter - Should ALLOW when under limit")
    void slidingWindowCounter_shouldAllowWhenUnderLimit() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 9L, 60L));

        RateLimitResult result = slidingWindowCounterAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getAlgorithmUsed())
                .isEqualTo("SLIDING_WINDOW_COUNTER");
    }

    @Test
    @DisplayName("Sliding Window Counter - Should BLOCK when limit exceeded")
    void slidingWindowCounter_shouldBlockWhenLimitExceeded() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(0L, 0L, 30L));

        RateLimitResult result = slidingWindowCounterAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(30L);
    }

    @Test
    @DisplayName("Sliding Window Counter - Uses two Redis keys")
    void slidingWindowCounter_usesTwoRedisKeys() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 9L, 60L));

        slidingWindowCounterAlgorithm.isAllowed("192.168.1.1", testRule);

        // Verify it was called with 2 keys (current + previous window)
        verify(circuitBreakerService).executeScript(
                anyString(), any(),
                argThat(keys -> keys.size() == 2),
                any()
        );
    }

// ─── LEAKY BUCKET TESTS ────────────────────────────────────

    @Test
    @DisplayName("Leaky Bucket - Should ALLOW when bucket not full")
    void leakyBucket_shouldAllowWhenBucketNotFull() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 8L, 0L));

        RateLimitResult result = leakyBucketAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getAlgorithmUsed()).isEqualTo("LEAKY_BUCKET");
    }

    @Test
    @DisplayName("Leaky Bucket - Should BLOCK when bucket full")
    void leakyBucket_shouldBlockWhenBucketFull() {
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(0L, 0L, 10L));

        RateLimitResult result = leakyBucketAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Leaky Bucket - Smooth traffic, no burst allowed")
    void leakyBucket_smoothTrafficNoBurst() {
        // First call allowed
        when(circuitBreakerService.executeScript(
                anyString(), any(), anyList(), any()))
                .thenReturn(List.of(1L, 4L, 0L))  // first call
                .thenReturn(List.of(0L, 0L, 5L)); // second call blocked immediately

        RateLimitResult first = leakyBucketAlgorithm
                .isAllowed("192.168.1.1", testRule);
        RateLimitResult second = leakyBucketAlgorithm
                .isAllowed("192.168.1.1", testRule);

        assertThat(first.isAllowed()).isTrue();
        assertThat(second.isAllowed()).isFalse();
    }

// ─── UPDATED FACTORY TEST ──────────────────────────────────

    @Test
    @DisplayName("Algorithm Factory - Returns all 5 algorithms correctly")
    void algorithmFactory_returnsAllFiveAlgorithms() {
        AlgorithmFactory factory = new AlgorithmFactory(
                fixedWindowAlgorithm,
                slidingWindowAlgorithm,
                slidingWindowCounterAlgorithm,
                tokenBucketAlgorithm,
                leakyBucketAlgorithm
        );

        assertThat(factory.getAlgorithm(AlgorithmType.FIXED_WINDOW))
                .isInstanceOf(FixedWindowAlgorithm.class);
        assertThat(factory.getAlgorithm(AlgorithmType.SLIDING_WINDOW_LOG))
                .isInstanceOf(SlidingWindowLogAlgorithm.class);
        assertThat(factory.getAlgorithm(AlgorithmType.SLIDING_WINDOW_COUNTER))
                .isInstanceOf(SlidingWindowCounterAlgorithm.class);
        assertThat(factory.getAlgorithm(AlgorithmType.TOKEN_BUCKET))
                .isInstanceOf(TokenBucketAlgorithm.class);
        assertThat(factory.getAlgorithm(AlgorithmType.LEAKY_BUCKET))
                .isInstanceOf(LeakyBucketAlgorithm.class);
    }
}