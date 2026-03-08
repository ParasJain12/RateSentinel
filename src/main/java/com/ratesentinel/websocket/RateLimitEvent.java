package com.ratesentinel.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitEvent {

    // What type of event
    private String eventType; // ALLOWED, BLOCKED, BLACKLISTED

    // Who made the request
    private String identifier;

    // Which endpoint
    private String endpoint;

    // HTTP method
    private String httpMethod;

    // Algorithm used
    private String algorithm;

    // Remaining requests
    private int remaining;

    // Total limit
    private int totalLimit;

    // User tier if present
    private String userTier;

    // Timestamp
    private String timestamp;

    // Stats snapshot for live counters
    private long totalAllowedToday;
    private long totalBlockedToday;
    private double blockRatePercentage;

}