package com.ratesentinel.controller;

import com.ratesentinel.annotation.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    // Basic test — 5 requests per minute via database rule
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "RateSentinel is working!");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "DATABASE_RULE");
        return ResponseEntity.ok(response);
    }

    // Annotation based — 3 requests per minute
    // No database entry needed
    @GetMapping("/hello")
    @RateLimit(limit = 3, windowSeconds = 60, algorithm = "FIXED_WINDOW")
    public ResponseEntity<Map<String, Object>> hello() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from RateSentinel!");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE");
        return ResponseEntity.ok(response);
    }

    // Annotation based — token bucket, 10 per minute
    @PostMapping("/data")
    @RateLimit(
            limit = 10,
            windowSeconds = 60,
            algorithm = "TOKEN_BUCKET",
            identifierType = "IP_ADDRESS"
    )
    public ResponseEntity<Map<String, Object>> postData(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "POST request received");
        response.put("receivedData", body);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE - TOKEN_BUCKET");
        return ResponseEntity.ok(response);
    }

    // Strict login endpoint — only 3 attempts per minute
    // Prevents brute force attacks
    @PostMapping("/login")
    @RateLimit(
            limit = 3,
            windowSeconds = 60,
            algorithm = "FIXED_WINDOW",
            identifierType = "IP_ADDRESS",
            message = "Too many login attempts. Please wait."
    )
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login endpoint - protected by @RateLimit");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE - BRUTE_FORCE_PROTECTION");
        return ResponseEntity.ok(response);
    }

    // OTP endpoint — only 2 OTPs per minute
    @PostMapping("/send-otp")
    @RateLimit(
            limit = 2,
            windowSeconds = 60,
            algorithm = "FIXED_WINDOW",
            identifierType = "IP_ADDRESS",
            message = "Too many OTP requests."
    )
    public ResponseEntity<Map<String, Object>> sendOtp(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "OTP sent - protected by @RateLimit");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE - OTP_PROTECTION");
        return ResponseEntity.ok(response);
    }

    // Per user rate limiting using USER_ID header
    @GetMapping("/profile")
    @RateLimit(
            limit = 10,
            windowSeconds = 60,
            algorithm = "SLIDING_WINDOW_LOG",
            identifierType = "USER_ID"
    )
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(value = "X-User-Id", required = false)
            String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile endpoint");
        response.put("userId", userId);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE - PER_USER");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @RateLimit(
            limit = 20,
            windowSeconds = 60,
            algorithm = "SLIDING_WINDOW_COUNTER",
            identifierType = "IP_ADDRESS"
    )
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Search endpoint");
        response.put("query", query);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE - SLIDING_WINDOW_COUNTER");
        return ResponseEntity.ok(response);
    }

    // Leaky Bucket — smoothest traffic, no bursting allowed
    @PostMapping("/upload")
    @RateLimit(
            limit = 5,
            windowSeconds = 60,
            algorithm = "LEAKY_BUCKET",
            identifierType = "IP_ADDRESS"
    )
    public ResponseEntity<Map<String, Object>> upload(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Upload endpoint - smooth rate limiting");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE - LEAKY_BUCKET");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/free-endpoint")
    public ResponseEntity<Map<String, Object>> freeEndpoint(
            @RequestHeader(value = "X-User-Tier", required = false)
            String tier) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Free tier endpoint");
        response.put("yourTier", tier != null ? tier : "NOT PROVIDED");
        response.put("limits", Map.of(
                "FREE", "10 req/min",
                "PRO", "100 req/min",
                "ENTERPRISE", "1000 req/min",
                "INTERNAL", "Unlimited"
        ));
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

}
