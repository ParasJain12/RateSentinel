package com.ratesentinel.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {

	// Was the request allowed?
    private boolean allowed;

    // How many requests remaining in current window
    private int remainingRequests;

    // Total limit for this rule
    private int totalLimit;

    // When does the window reset (unix timestamp)
    private long resetTimeSeconds;

    // How many seconds to wait before retrying
    private long retryAfterSeconds;

    // Which algorithm made this decision
    private String algorithmUsed;

    // Current request count
    private long currentCount;

    // Convenience method for allowed result
    public static RateLimitResult allowed(int remaining, int total, 
                                          long resetTime, String algorithm) {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingRequests(remaining)
                .totalLimit(total)
                .resetTimeSeconds(resetTime)
                .retryAfterSeconds(0)
                .algorithmUsed(algorithm)
                .build();
    }

    // Convenience method for blocked result
    public static RateLimitResult blocked(int total, long resetTime, 
                                          long retryAfter, String algorithm) {
        return RateLimitResult.builder()
                .allowed(false)
                .remainingRequests(0)
                .totalLimit(total)
                .resetTimeSeconds(resetTime)
                .retryAfterSeconds(retryAfter)
                .algorithmUsed(algorithm)
                .build();
    }

}
