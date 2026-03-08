package com.ratesentinel.controller;

import com.ratesentinel.service.RedisCircuitBreakerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Health",
        description = "System health, Redis status, DB status and JVM metrics")
public class HealthController {

    private final RedissonClient redissonClient;
    private final RedisCircuitBreakerService circuitBreakerService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.application.name:RateSentinel}")
    private String appName;

    // ─── OVERALL HEALTH ────────────────────────────────────────

    @Operation(
            summary = "Overall system health",
            description = """
            Returns complete health status of all system components:
            - Application status
            - Redis connectivity
            - MySQL connectivity
            - Circuit Breaker state
            - JVM memory usage
            """
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOverallHealth() {
        Map<String, Object> health = new LinkedHashMap<>();

        // App info
        health.put("application", appName);
        health.put("timestamp", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("status", "UP");

        // Check Redis
        Map<String, Object> redisHealth = checkRedisHealth();
        health.put("redis", redisHealth);

        // Check MySQL
        Map<String, Object> dbHealth = checkDatabaseHealth();
        health.put("database", dbHealth);

        // Circuit Breaker
        RedisCircuitBreakerService.CircuitBreakerMetrics cbMetrics =
                circuitBreakerService.getMetrics();
        health.put("circuitBreaker", Map.of(
                "state", cbMetrics.state(),
                "failureRate", cbMetrics.failureRate(),
                "status", cbMetrics.state().equals("CLOSED") ? "HEALTHY" : "DEGRADED"
        ));

        // JVM
        health.put("jvm", getJvmMetrics());

        // Overall status
        boolean redisUp = "UP".equals(redisHealth.get("status"));
        boolean dbUp = "UP".equals(dbHealth.get("status"));

        if (!redisUp || !dbUp) {
            health.put("status", "DEGRADED");
        }

        return ResponseEntity.ok(health);
    }

    // ─── REDIS HEALTH ──────────────────────────────────────────

    @Operation(
            summary = "Redis health check",
            description = """
            Checks Redis connectivity by sending a PING command.
            Also shows circuit breaker state and key count.
            """
    )
    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> getRedisHealth() {
        return ResponseEntity.ok(checkRedisHealth());
    }

    // ─── DATABASE HEALTH ───────────────────────────────────────

    @Operation(
            summary = "MySQL database health check",
            description = """
            Checks MySQL connectivity and returns table row counts:
            - rate_limit_rules
            - rate_limit_logs
            - tier_configs
            - whitelist
            - blacklist
            """
    )
    @GetMapping("/database")
    public ResponseEntity<Map<String, Object>> getDatabaseHealth() {
        return ResponseEntity.ok(checkDatabaseHealth());
    }

    // ─── JVM METRICS ───────────────────────────────────────────

    @Operation(
            summary = "JVM memory and runtime metrics",
            description = """
            Returns JVM metrics including:
            - Heap memory used vs max
            - Non-heap memory
            - JVM uptime
            - Available processors
            """
    )
    @GetMapping("/jvm")
    public ResponseEntity<Map<String, Object>> getJvmHealth() {
        return ResponseEntity.ok(getJvmMetrics());
    }

    // ─── CIRCUIT BREAKER ───────────────────────────────────────

    @Operation(
            summary = "Circuit breaker detailed status",
            description = """
            Returns detailed Redis circuit breaker metrics:
            - **CLOSED** — Redis healthy, normal operation
            - **OPEN** — Redis down, requests failing fast
            - **HALF_OPEN** — Testing Redis recovery
            
            Failure rate threshold: 50%
            Wait duration in open state: 30 seconds
            """
    )
    @GetMapping("/circuit-breaker")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerHealth() {
        RedisCircuitBreakerService.CircuitBreakerMetrics metrics =
                circuitBreakerService.getMetrics();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("state", metrics.state());
        response.put("failureRate", metrics.failureRate() + "%");
        response.put("successfulCalls", metrics.successfulCalls());
        response.put("failedCalls", metrics.failedCalls());
        response.put("notPermittedCalls", metrics.notPermittedCalls());
        response.put("interpretation", interpretCircuitState(metrics.state()));
        response.put("timestamp", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return ResponseEntity.ok(response);
    }

    // ─── PING ──────────────────────────────────────────────────

    @Operation(
            summary = "Simple ping",
            description = "Lightweight ping to check if application is running"
    )
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "PONG",
                "application", appName,
                "timestamp", LocalDateTime.now()
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        ));
    }

    // ─── SYSTEM INFO ───────────────────────────────────────────

    @Operation(
            summary = "System information",
            description = "Returns application configuration and system information"
    )
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("application", appName);
        info.put("version", "1.0.0");
        info.put("description", "Distributed Rate Limiting System");

        info.put("algorithms", new String[]{
                "FIXED_WINDOW",
                "SLIDING_WINDOW_LOG",
                "SLIDING_WINDOW_COUNTER",
                "TOKEN_BUCKET",
                "LEAKY_BUCKET"
        });

        info.put("userTiers", new String[]{
                "FREE — 10 req/min",
                "PRO — 100 req/min",
                "ENTERPRISE — 1000 req/min",
                "INTERNAL — Unlimited"
        });

        info.put("features", new String[]{
                "Per endpoint rate limiting",
                "Per user tier rate limiting",
                "Custom @RateLimit annotation",
                "Redis circuit breaker",
                "Rule caching with Redis",
                "Whitelist / Blacklist",
                "Real time dashboard",
                "Async analytics logging"
        });

        info.put("endpoints", Map.of(
                "dashboard", "http://localhost:8080/dashboard",
                "swagger", "http://localhost:8080/swagger-ui.html",
                "health", "http://localhost:8080/api/health"
        ));

        info.put("timestamp", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return ResponseEntity.ok(info);
    }

    // ─── PRIVATE HELPERS ───────────────────────────────────────

    private Map<String, Object> checkRedisHealth() {
        Map<String, Object> redisHealth = new LinkedHashMap<>();
        try {
            // Ping Redis
            redissonClient.getBucket("health:ping").set("pong");
            Object pong = redissonClient.getBucket("health:ping").get();
            redissonClient.getBucket("health:ping").delete();

            // Get key count
            long keyCount = redissonClient.getKeys().count();

            // Circuit breaker state
            String cbState = circuitBreakerService.getCircuitBreakerState();

            redisHealth.put("status", "UP");
            redisHealth.put("ping", "PONG");
            redisHealth.put("totalKeys", keyCount);
            redisHealth.put("circuitBreakerState", cbState);
            redisHealth.put("host", "localhost");
            redisHealth.put("port", 6379);

        } catch (Exception e) {
            redisHealth.put("status", "DOWN");
            redisHealth.put("error", e.getMessage());
            redisHealth.put("circuitBreakerState",
                    circuitBreakerService.getCircuitBreakerState());
        }
        return redisHealth;
    }

    private Map<String, Object> checkDatabaseHealth() {
        Map<String, Object> dbHealth = new LinkedHashMap<>();
        try {
            // Simple query to verify connectivity
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            // Get table counts
            Long rulesCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM rate_limit_rules", Long.class);
            Long logsCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM rate_limit_logs", Long.class);
            Long tierCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tier_configs", Long.class);
            Long whitelistCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM whitelist", Long.class);
            Long blacklistCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM blacklist", Long.class);

            dbHealth.put("status", "UP");
            dbHealth.put("database", "MySQL");
            dbHealth.put("tableCounts", Map.of(
                    "rate_limit_rules", rulesCount,
                    "rate_limit_logs", logsCount,
                    "tier_configs", tierCount,
                    "whitelist", whitelistCount,
                    "blacklist", blacklistCount
            ));

        } catch (Exception e) {
            dbHealth.put("status", "DOWN");
            dbHealth.put("error", e.getMessage());
        }
        return dbHealth;
    }

    private Map<String, Object> getJvmMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMB", heapUsed / (1024 * 1024));
        jvm.put("heapMaxMB", heapMax / (1024 * 1024));
        jvm.put("heapUsagePercent",
                Math.round((double) heapUsed / heapMax * 100));
        jvm.put("nonHeapUsedMB", nonHeapUsed / (1024 * 1024));
        jvm.put("uptimeSeconds", runtimeBean.getUptime() / 1000);
        jvm.put("availableProcessors",
                Runtime.getRuntime().availableProcessors());
        jvm.put("javaVersion", System.getProperty("java.version"));

        return jvm;
    }

    private String interpretCircuitState(String state) {
        return switch (state) {
            case "CLOSED" ->
                    "Redis is healthy. All requests passing through normally.";
            case "OPEN" ->
                    "Redis is down. Requests failing fast. Check Redis connection.";
            case "HALF_OPEN" ->
                    "Testing Redis recovery. Allowing limited requests through.";
            default -> "Unknown state.";
        };
    }

}