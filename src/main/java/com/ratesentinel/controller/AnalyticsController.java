package com.ratesentinel.controller;

import com.ratesentinel.dto.AnalyticsDTO;
import com.ratesentinel.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // Main dashboard data
    @GetMapping("/dashboard")
    public ResponseEntity<AnalyticsDTO> getDashboard() {
        return ResponseEntity.ok(analyticsService.getDashboardAnalytics());
    }

    // Top blocked identifiers
    @GetMapping("/top-blocked")
    public ResponseEntity<List<Map<String, Object>>> getTopBlocked() {
        return ResponseEntity.ok(analyticsService.getTopBlockedIdentifiers(
                java.time.LocalDateTime.now().minusHours(24)));
    }

    // Recent activity feed
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(analyticsService.getRecentLogs(limit));
    }

}