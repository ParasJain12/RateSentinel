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
    private final SlidingWindowCounterAlgorithm slidingWindowCounterAlgorithm;
    private final TokenBucketAlgorithm tokenBucketAlgorithm;
    private final LeakyBucketAlgorithm leakyBucketAlgorithm;

    public RateLimitAlgorithm getAlgorithm(AlgorithmType type) {
        switch (type) {
            case FIXED_WINDOW:
                return fixedWindowAlgorithm;
            case SLIDING_WINDOW_LOG:
                return slidingWindowLogAlgorithm;
            case SLIDING_WINDOW_COUNTER:
                return slidingWindowCounterAlgorithm;
            case TOKEN_BUCKET:
                return tokenBucketAlgorithm;
            case LEAKY_BUCKET:
                return leakyBucketAlgorithm;
            default:
                log.warn("Unknown algorithm {}, using FixedWindow", type);
                return fixedWindowAlgorithm;
        }
    }

}