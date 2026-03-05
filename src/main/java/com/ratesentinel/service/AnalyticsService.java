package com.ratesentinel.service;

import com.ratesentinel.dto.AnalyticsDTO;
import com.ratesentinel.repository.BlacklistRepository;
import com.ratesentinel.repository.RateLimitLogRepository;
import com.ratesentinel.repository.RateLimitRuleRepository;
import com.ratesentinel.repository.WhitelistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final RateLimitLogRepository logRepository;
    private final RateLimitRuleRepository ruleRepository;
    private final WhitelistRepository whitelistRepository;
    private final BlacklistRepository blacklistRepository;

    /**
     * Get complete analytics for dashboard
     */
    public AnalyticsDTO getDashboardAnalytics() {

        LocalDateTime since = LocalDateTime.now().minusHours(24);

        // Total allowed vs blocked today
        long totalAllowed = 0;
        long totalBlocked = 0;

        List<Object[]> decisionCounts = logRepository.countByDecisionSince(since);
        for (Object[] row : decisionCounts) {
            String decision = (String) row[0];
            Long count = (Long) row[1];
            if ("ALLOW".equals(decision)) totalAllowed = count;
            else if ("BLOCK".equals(decision)) totalBlocked = count;
        }

        long totalRequests = totalAllowed + totalBlocked;
        double blockRate = totalRequests > 0
                ? (double) totalBlocked / totalRequests * 100 : 0;

        // Top blocked identifiers
        List<Map<String, Object>> topBlocked = getTopBlockedIdentifiers(since);

        // Hourly stats for graph
        List<Map<String, Object>> hourly = getHourlyStats(since);

        return AnalyticsDTO.builder()
                .totalRequestsToday(totalRequests)
                .totalAllowedToday(totalAllowed)
                .totalBlockedToday(totalBlocked)
                .blockRatePercentage(Math.round(blockRate * 100.0) / 100.0)
                .topBlockedIdentifiers(topBlocked)
                .hourlyStats(hourly)
                .activeRulesCount(ruleRepository.findByIsActiveTrue().size())
                .whitelistedIpsCount(whitelistRepository.count())
                .blacklistedIpsCount(blacklistRepository.count())
                .build();
    }

    /**
     * Get top 10 most blocked identifiers
     */
    public List<Map<String, Object>> getTopBlockedIdentifiers(LocalDateTime since) {
        List<Object[]> results = logRepository.findTopBlockedIdentifiers(since);
        List<Map<String, Object>> topBlocked = new ArrayList<>();

        int limit = Math.min(results.size(), 10);
        for (int i = 0; i < limit; i++) {
            Object[] row = results.get(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put("identifier", row[0]);
            entry.put("blockCount", row[1]);
            topBlocked.add(entry);
        }
        return topBlocked;
    }

    /**
     * Get hourly traffic breakdown for last 24 hours
     */
    public List<Map<String, Object>> getHourlyStats(LocalDateTime since) {
        List<Object[]> results = logRepository.getHourlyStats(since);
        Map<Integer, Map<String, Object>> hourMap = new HashMap<>();

        // Initialize all 24 hours with 0
        for (int i = 0; i < 24; i++) {
            Map<String, Object> hourData = new HashMap<>();
            hourData.put("hour", i);
            hourData.put("allowed", 0L);
            hourData.put("blocked", 0L);
            hourMap.put(i, hourData);
        }

        // Fill in actual data
        for (Object[] row : results) {
            int hour = ((Number) row[0]).intValue();
            String decision = (String) row[1];
            Long count = (Long) row[2];

            if (hourMap.containsKey(hour)) {
                if ("ALLOW".equals(decision)) {
                    hourMap.get(hour).put("allowed", count);
                } else if ("BLOCK".equals(decision)) {
                    hourMap.get(hour).put("blocked", count);
                }
            }
        }

        return new ArrayList<>(hourMap.values())
                .stream()
                .sorted((a, b) -> Integer.compare(
                        (Integer) a.get("hour"),
                        (Integer) b.get("hour")))
                .toList();
    }

    /**
     * Get recent logs for live feed on dashboard
     */
    public List<Map<String, Object>> getRecentLogs(int limit) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(30);
        List<Object[]> raw = logRepository.getHourlyStats(since);
        List<Map<String, Object>> recent = new ArrayList<>();

        logRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                        since, LocalDateTime.now())
                .stream()
                .limit(limit)
                .forEach(log -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("identifier", log.getIdentifier());
                    entry.put("endpoint", log.getEndpoint());
                    entry.put("decision", log.getDecision());
                    entry.put("algorithm", log.getAlgorithm());
                    entry.put("remaining", log.getRemainingRequests());
                    entry.put("ipAddress", log.getIpAddress());
                    entry.put("time", log.getCreatedAt());
                    recent.add(entry);
                });

        return recent;
    }

}