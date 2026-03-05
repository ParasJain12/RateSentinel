package com.ratesentinel.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {

        // Default config for all circuit breakers
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()

                // Open circuit after 50% failure rate
                .failureRateThreshold(50)

                // Minimum 5 calls before calculating failure rate
                // Prevents opening on first failure
                .minimumNumberOfCalls(5)

                // Sliding window of last 10 calls
                .slidingWindowSize(10)

                // Stay OPEN for 30 seconds before trying again
                .waitDurationInOpenState(Duration.ofSeconds(30))

                // In HALF-OPEN state, allow 3 test requests
                .permittedNumberOfCallsInHalfOpenState(3)

                // Auto transition from OPEN to HALF-OPEN after wait
                .automaticTransitionFromOpenToHalfOpenEnabled(true)

                // These exceptions trigger the circuit breaker
                .recordExceptions(
                        Exception.class,
                        RuntimeException.class
                )

                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Register event listeners for logging state changes
        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    CircuitBreaker cb = event.getAddedEntry();

                    // Log every state transition
                    cb.getEventPublisher()
                            .onStateTransition(e -> log.warn(
                                    "CircuitBreaker [{}] state changed: {} → {}",
                                    cb.getName(),
                                    e.getStateTransition().getFromState(),
                                    e.getStateTransition().getToState()
                            ))
                            .onFailureRateExceeded(e -> log.error(
                                    "CircuitBreaker [{}] failure rate exceeded: {}%",
                                    cb.getName(),
                                    e.getFailureRate()
                            ))
                            .onCallNotPermitted(e -> log.warn(
                                    "CircuitBreaker [{}] call blocked - circuit is OPEN",
                                    cb.getName()
                            ));
                });

        return registry;
    }

    @Bean
    public CircuitBreaker redisCircuitBreaker(
            CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("redis");
    }

}