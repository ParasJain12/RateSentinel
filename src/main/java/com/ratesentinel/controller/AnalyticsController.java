package com.ratesentinel.controller;

import com.ratesentinel.dto.AnalyticsDTO;
import com.ratesentinel.service.AnalyticsService;
import com.ratesentinel.service.RedisCircuitBreakerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics",
        description = "Traffic analytics, stats and circuit breaker status")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final RedisCircuitBreakerService circuitBreakerService;

    @Operation(
            summary = "Get dashboard analytics",
            description = """
            Returns complete analytics for the last 24 hours including:
            - Total requests allowed vs blocked
            - Block rate percentage
            - Top blocked identifiers
            - Hourly traffic breakdown
            - Active rules count
            - Whitelist/Blacklist counts
            """
    )
    @GetMapping("/dashboard")
    public ResponseEntity<AnalyticsDTO> getDashboard() {
        return ResponseEntity.ok(analyticsService.getDashboardAnalytics());
    }

    @Operation(
            summary = "Get top blocked identifiers",
            description = "Returns top 10 most blocked IPs or user IDs in last 24 hours"
    )
    @GetMapping("/top-blocked")
    public ResponseEntity<List<Map<String, Object>>> getTopBlocked() {
        return ResponseEntity.ok(analyticsService.getTopBlockedIdentifiers(
                java.time.LocalDateTime.now().minusHours(24)));
    }

    @Operation(
            summary = "Get recent activity feed",
            description = "Returns recent rate limit decisions for live monitoring"
    )
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(analyticsService.getRecentLogs(limit));
    }

    @Operation(
            summary = "Get circuit breaker status",
            description = """
            Returns current Redis circuit breaker state:
            - **CLOSED** — Redis healthy, all requests going through
            - **OPEN** — Redis down, requests failing fast with fallback
            - **HALF_OPEN** — Testing if Redis recovered
            """
    )
    @GetMapping("/circuit-breaker")
    public ResponseEntity<RedisCircuitBreakerService.CircuitBreakerMetrics>
    getCircuitBreakerStatus() {
        return ResponseEntity.ok(circuitBreakerService.getMetrics());
    }

}