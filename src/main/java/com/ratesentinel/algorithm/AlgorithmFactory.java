package com.ratesentinel.algorithm;

import com.ratesentinel.model.AlgorithmType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlgorithmFactory {

    private final FixedWindowAlgorithm fixedWindowAlgorithm;
    private final SlidingWindowLogAlgorithm slidingWindowLogAlgorithm;
    private final TokenBucketAlgorithm tokenBucketAlgorithm;

    public RateLimitAlgorithm getAlgorithm(AlgorithmType type) {
        switch (type) {
            case FIXED_WINDOW:
                return fixedWindowAlgorithm;
            case SLIDING_WINDOW_LOG:
            case SLIDING_WINDOW_COUNTER:
                return slidingWindowLogAlgorithm;
            case TOKEN_BUCKET:
                return tokenBucketAlgorithm;
            case LEAKY_BUCKET:
                // Falls back to sliding window for now
                // We will implement this separately
                log.warn("LeakyBucket not yet implemented, using SlidingWindow");
                return slidingWindowLogAlgorithm;
            default:
                log.warn("Unknown algorithm type {}, using FixedWindow", type);
                return fixedWindowAlgorithm;
        }
    }

}