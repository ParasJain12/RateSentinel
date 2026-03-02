package com.ratesentinel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitDecisionDTO {

    private boolean allowed;
    private String identifier;
    private String endpoint;
    private String httpMethod;
    private int remainingRequests;
    private int totalLimit;
    private long resetTimeSeconds;
    private long retryAfterSeconds;
    private String algorithmUsed;
    private String reason;

}