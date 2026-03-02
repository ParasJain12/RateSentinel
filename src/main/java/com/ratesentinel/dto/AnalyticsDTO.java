package com.ratesentinel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDTO {

    private long totalRequestsToday;
    private long totalAllowedToday;
    private long totalBlockedToday;
    private double blockRatePercentage;
    private List<Map<String, Object>> topBlockedIdentifiers;
    private List<Map<String, Object>> hourlyStats;
    private long activeRulesCount;
    private long whitelistedIpsCount;
    private long blacklistedIpsCount;

}