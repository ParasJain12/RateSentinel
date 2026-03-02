package com.ratesentinel.exception;

import com.ratesentinel.algorithm.RateLimitResult;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitResult rateLimitResult;
    private final String identifier;
    private final String endpoint;

    public RateLimitExceededException(RateLimitResult result,
                                      String identifier,
                                      String endpoint) {
        super("Rate limit exceeded for identifier: " + identifier);
        this.rateLimitResult = result;
        this.identifier = identifier;
        this.endpoint = endpoint;
    }

}