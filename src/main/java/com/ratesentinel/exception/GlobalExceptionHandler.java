package com.ratesentinel.exception;

import com.ratesentinel.algorithm.RateLimitResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(
            RateLimitExceededException ex) {

        RateLimitResult result = ex.getRateLimitResult();

        Map<String, Object> response = new HashMap<>();
        response.put("status", 429);
        response.put("error", "Too Many Requests");
        response.put("message", "Rate limit exceeded. Please slow down.");
        response.put("identifier", ex.getIdentifier());
        response.put("endpoint", ex.getEndpoint());
        response.put("retryAfterSeconds", result.getRetryAfterSeconds());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit",
                        String.valueOf(result.getTotalLimit()))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset",
                        String.valueOf(result.getResetTimeSeconds()))
                .header("Retry-After",
                        String.valueOf(result.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("status", 500);
        response.put("error", "Internal Server Error");
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // Add this handler method
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Map<String, Object>> handleCircuitBreakerOpen(
            CallNotPermittedException ex) {
        log.warn("Circuit breaker is OPEN: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Request processed in degraded mode");
        response.put("note", "Cache unavailable, serving from database");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    // Also add handler for general Redis errors
    @ExceptionHandler(org.redisson.client.RedisException.class)
    public ResponseEntity<Map<String, Object>> handleRedisException(
            RuntimeException ex) {
        log.error("Redis error caught at handler level: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Request processed in degraded mode");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

}