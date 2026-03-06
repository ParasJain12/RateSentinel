package com.ratesentinel.concurrent;

import com.ratesentinel.algorithm.FixedWindowAlgorithm;
import com.ratesentinel.algorithm.RateLimitResult;
import com.ratesentinel.algorithm.SlidingWindowLogAlgorithm;
import com.ratesentinel.algorithm.TokenBucketAlgorithm;
import com.ratesentinel.model.AlgorithmType;
import com.ratesentinel.model.IdentifierType;
import com.ratesentinel.model.RateLimitRule;
import com.ratesentinel.service.RedisCircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONCURRENCY TESTS
 *
 * These tests prove the rate limiter works correctly
 * under real concurrent load with ZERO race conditions.
 *
 * This is the most impressive demo for interviews.
 */
@DisplayName("Concurrency Tests - Proving Zero Race Conditions")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyTest {

    private static RedissonClient redissonClient;
    private static RedisCircuitBreakerService circuitBreakerService;
    private static FixedWindowAlgorithm fixedWindowAlgorithm;
    private static SlidingWindowLogAlgorithm slidingWindowAlgorithm;
    private static TokenBucketAlgorithm tokenBucketAlgorithm;

    @BeforeAll
    static void setUp() {
        // Connect to real Redis for concurrency testing
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setConnectionPoolSize(50)
                .setConnectionMinimumIdleSize(10);

        redissonClient = Redisson.create(config);

        // Create circuit breaker
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("redis-test");

        circuitBreakerService = new RedisCircuitBreakerService(
                redissonClient, circuitBreaker);

        fixedWindowAlgorithm = new FixedWindowAlgorithm(circuitBreakerService);
        slidingWindowAlgorithm = new SlidingWindowLogAlgorithm(circuitBreakerService);
        tokenBucketAlgorithm = new TokenBucketAlgorithm(circuitBreakerService);
    }

    @AfterAll
    static void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @BeforeEach
    void cleanRedis() {
        // Clean test keys before each test
        redissonClient.getKeys().deleteByPattern("rate:test:*");
    }

    // ─── TEST 1 ────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("TEST 1 - Exactly N requests allowed, no more, no less")
    void exactlyNRequestsAllowed() throws InterruptedException {

        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST 1: Exact count verification");
        System.out.println("Limit: 10 | Threads: 50 | Expected allowed: 10");
        System.out.println("=".repeat(60));

        int LIMIT = 10;
        int TOTAL_THREADS = 50;

        RateLimitRule rule = buildRule(LIMIT, 60, AlgorithmType.FIXED_WINDOW);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        // Use CountDownLatch to make all threads start at exactly same time
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(TOTAL_THREADS);

        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);

        for (int i = 0; i < TOTAL_THREADS; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for start signal
                    RateLimitResult result = fixedWindowAlgorithm
                            .isAllowed("test-exact-count", rule);

                    if (result.isAllowed()) allowedCount.incrementAndGet();
                    else blockedCount.incrementAndGet();

                } catch (Exception e) {
                    blockedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire! All threads start simultaneously
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Allowed: " + allowedCount.get());
        System.out.println("Blocked: " + blockedCount.get());
        System.out.println("Total:   " + (allowedCount.get() + blockedCount.get()));

        // CRITICAL ASSERTION — exactly LIMIT requests allowed, no more
        assertThat(allowedCount.get())
                .as("Exactly %d requests should be allowed, no race conditions", LIMIT)
                .isEqualTo(LIMIT);

        assertThat(blockedCount.get())
                .as("Remaining %d requests should be blocked", TOTAL_THREADS - LIMIT)
                .isEqualTo(TOTAL_THREADS - LIMIT);

        System.out.println("✅ TEST 1 PASSED - Zero race conditions confirmed!");
    }

    // ─── TEST 2 ────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("TEST 2 - 100 concurrent threads, only limit allowed")
    void hundredConcurrentThreads() throws InterruptedException {

        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST 2: 100 concurrent threads stress test");
        System.out.println("Limit: 20 | Threads: 100 | Expected allowed: 20");
        System.out.println("=".repeat(60));

        int LIMIT = 20;
        int TOTAL_THREADS = 100;

        RateLimitRule rule = buildRule(LIMIT, 60, AlgorithmType.FIXED_WINDOW);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(TOTAL_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);

        for (int i = 0; i < TOTAL_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult result = fixedWindowAlgorithm
                            .isAllowed("test-100-threads", rule);

                    if (result.isAllowed()) allowedCount.incrementAndGet();
                    else blockedCount.incrementAndGet();

                } catch (Exception e) {
                    errors.add("Thread " + threadId + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Allowed: " + allowedCount.get());
        System.out.println("Blocked: " + blockedCount.get());
        System.out.println("Errors:  " + errors.size());

        assertThat(errors).as("No errors should occur during concurrent execution")
                .isEmpty();
        assertThat(allowedCount.get())
                .as("Exactly %d requests allowed", LIMIT)
                .isEqualTo(LIMIT);

        System.out.println("✅ TEST 2 PASSED - 100 threads handled correctly!");
    }

    // ─── TEST 3 ────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("TEST 3 - Multiple users independent counters")
    void multipleUsersIndependentCounters() throws InterruptedException {

        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST 3: Multiple users independent counters");
        System.out.println("5 users × 10 threads each | Limit: 5 per user");
        System.out.println("=".repeat(60));

        int LIMIT = 5;
        int USERS = 5;
        int THREADS_PER_USER = 10;
        int TOTAL_THREADS = USERS * THREADS_PER_USER;

        RateLimitRule rule = buildRule(LIMIT, 60, AlgorithmType.FIXED_WINDOW);

        // Track allowed count per user
        ConcurrentHashMap<String, AtomicInteger> allowedPerUser =
                new ConcurrentHashMap<>();
        for (int u = 0; u < USERS; u++) {
            allowedPerUser.put("user-" + u, new AtomicInteger(0));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(TOTAL_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);

        for (int u = 0; u < USERS; u++) {
            final String userId = "user-" + u;
            for (int t = 0; t < THREADS_PER_USER; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        RateLimitResult result = fixedWindowAlgorithm
                                .isAllowed(userId, rule);

                        if (result.isAllowed()) {
                            allowedPerUser.get(userId).incrementAndGet();
                        }
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Results per user:");
        allowedPerUser.forEach((user, count) ->
                System.out.println("  " + user + " → allowed: " + count.get()));

        // Each user should have exactly LIMIT requests allowed
        allowedPerUser.forEach((user, count) ->
                assertThat(count.get())
                        .as("User %s should have exactly %d requests allowed", user, LIMIT)
                        .isEqualTo(LIMIT)
        );

        System.out.println("✅ TEST 3 PASSED - User isolation confirmed!");
    }

    // ─── TEST 4 ────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("TEST 4 - Sliding Window algorithm concurrency")
    void slidingWindowConcurrency() throws InterruptedException {

        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST 4: Sliding Window concurrency");
        System.out.println("Limit: 15 | Threads: 60");
        System.out.println("=".repeat(60));

        int LIMIT = 15;
        int TOTAL_THREADS = 60;

        RateLimitRule rule = buildRule(
                LIMIT, 60, AlgorithmType.SLIDING_WINDOW_LOG);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(TOTAL_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);

        for (int i = 0; i < TOTAL_THREADS; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult result = slidingWindowAlgorithm
                            .isAllowed("test-sliding", rule);

                    if (result.isAllowed()) allowedCount.incrementAndGet();
                    else blockedCount.incrementAndGet();

                } catch (Exception e) {
                    blockedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Allowed: " + allowedCount.get());
        System.out.println("Blocked: " + blockedCount.get());

        assertThat(allowedCount.get())
                .as("Sliding window should allow exactly %d", LIMIT)
                .isEqualTo(LIMIT);

        System.out.println("✅ TEST 4 PASSED - Sliding Window concurrency confirmed!");
    }

    // ─── TEST 5 ────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("TEST 5 - Algorithm comparison same conditions")
    void algorithmComparisonSameConditions() throws InterruptedException {

        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST 5: Algorithm comparison");
        System.out.println("Testing Fixed Window vs Sliding Window");
        System.out.println("=".repeat(60));

        int LIMIT = 10;
        int THREADS = 30;

        RateLimitRule fixedRule = buildRule(
                LIMIT, 60, AlgorithmType.FIXED_WINDOW);
        RateLimitRule slidingRule = buildRule(
                LIMIT, 60, AlgorithmType.SLIDING_WINDOW_LOG);

        AtomicInteger fixedAllowed = new AtomicInteger(0);
        AtomicInteger slidingAllowed = new AtomicInteger(0);

        // Test Fixed Window
        CountDownLatch startLatch1 = new CountDownLatch(1);
        CountDownLatch doneLatch1 = new CountDownLatch(THREADS);
        ExecutorService executor1 = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            executor1.submit(() -> {
                try {
                    startLatch1.await();
                    RateLimitResult result = fixedWindowAlgorithm
                            .isAllowed("test-compare-fixed", fixedRule);
                    if (result.isAllowed()) fixedAllowed.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch1.countDown();
                }
            });
        }
        startLatch1.countDown();
        doneLatch1.await(30, TimeUnit.SECONDS);
        executor1.shutdown();

        // Test Sliding Window
        CountDownLatch startLatch2 = new CountDownLatch(1);
        CountDownLatch doneLatch2 = new CountDownLatch(THREADS);
        ExecutorService executor2 = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            executor2.submit(() -> {
                try {
                    startLatch2.await();
                    RateLimitResult result = slidingWindowAlgorithm
                            .isAllowed("test-compare-sliding", slidingRule);
                    if (result.isAllowed()) slidingAllowed.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch2.countDown();
                }
            });
        }
        startLatch2.countDown();
        doneLatch2.await(30, TimeUnit.SECONDS);
        executor2.shutdown();

        System.out.println("Fixed Window allowed:   " + fixedAllowed.get());
        System.out.println("Sliding Window allowed: " + slidingAllowed.get());

        assertThat(fixedAllowed.get()).isEqualTo(LIMIT);
        assertThat(slidingAllowed.get()).isEqualTo(LIMIT);

        System.out.println("✅ TEST 5 PASSED - Both algorithms consistent!");
    }

    // ─── HELPER ────────────────────────────────────────────────

    private RateLimitRule buildRule(int limit, int windowSeconds,
                                    AlgorithmType algorithm) {
        return RateLimitRule.builder()
                .endpointPattern("/api/test-concurrent")
                .httpMethod("GET")
                .limitCount(limit)
                .windowSeconds(windowSeconds)
                .algorithm(algorithm)
                .identifierType(IdentifierType.IP_ADDRESS)
                .isActive(true)
                .build();
    }

}