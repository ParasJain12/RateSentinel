package com.ratesentinel.algorithm;

import com.ratesentinel.model.RateLimitRule;

public interface RateLimitAlgorithm {

	 /**
     * Check if request should be allowed or blocked
     * @param identifier - who is making the request (IP, userId, apiKey)
     * @param rule - the rate limit rule to apply
     * @return RateLimitResult containing decision and remaining requests
     */
    RateLimitResult isAllowed(String identifier, RateLimitRule rule);

    /**
     * Get algorithm name
     */
    String getAlgorithmName();
}
