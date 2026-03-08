package com.ratesentinel.controller;

import com.ratesentinel.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Test Endpoints",
        description = "Demo endpoints to test rate limiting features")
public class TestController {

    @Operation(
            summary = "Basic test endpoint",
            description = "Uses DATABASE rule — limited by rule in rate_limit_rules table"
    )
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "RateSentinel is working!");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "DATABASE_RULE");
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Hello endpoint",
            description = """
            Uses @RateLimit ANNOTATION — limit 3 per minute.
            No database entry needed.
            Try hitting more than 3 times to see 429 response.
            """
    )
    @GetMapping("/hello")
    @RateLimit(limit = 3, windowSeconds = 60, algorithm = "FIXED_WINDOW")
    public ResponseEntity<Map<String, Object>> hello() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from RateSentinel!");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("ruleType", "ANNOTATION_RULE - limit 3/min");
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Login endpoint",
            description = """
            Brute force protection — only 3 attempts per minute per IP.
            Uses FIXED_WINDOW algorithm.
            """
    )
    @PostMapping("/login")
    @RateLimit(
            limit = 3,
            windowSeconds = 60,
            algorithm = "FIXED_WINDOW",
            identifierType = "IP_ADDRESS"
    )
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login - protected by @RateLimit");
        response.put("ruleType", "ANNOTATION_RULE - 3 attempts/min");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "OTP endpoint",
            description = "OTP spam protection — only 2 OTPs per minute per IP"
    )
    @PostMapping("/send-otp")
    @RateLimit(
            limit = 2,
            windowSeconds = 60,
            algorithm = "FIXED_WINDOW",
            identifierType = "IP_ADDRESS"
    )
    public ResponseEntity<Map<String, Object>> sendOtp(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "OTP sent - protected by @RateLimit");
        response.put("ruleType", "ANNOTATION_RULE - 2 OTPs/min");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Profile endpoint - per user limiting",
            description = """
            Rate limited per USER ID not per IP.
            Add header: X-User-Id: anyUserId
            Each user gets their own independent counter.
            """
    )
    @GetMapping("/profile")
    @RateLimit(
            limit = 10,
            windowSeconds = 60,
            algorithm = "SLIDING_WINDOW_LOG",
            identifierType = "USER_ID"
    )
    public ResponseEntity<Map<String, Object>> getProfile(
            @Parameter(description = "User ID header")
            @RequestHeader(value = "X-User-Id", required = false)
            String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile endpoint");
        response.put("userId", userId);
        response.put("ruleType", "ANNOTATION_RULE - per USER_ID");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Search endpoint - Sliding Window Counter",
            description = "Uses SLIDING_WINDOW_COUNTER algorithm — most memory efficient"
    )
    @GetMapping("/search")
    @RateLimit(
            limit = 20,
            windowSeconds = 60,
            algorithm = "SLIDING_WINDOW_COUNTER"
    )
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Search results");
        response.put("query", query);
        response.put("ruleType", "ANNOTATION_RULE - SLIDING_WINDOW_COUNTER");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Upload endpoint - Leaky Bucket",
            description = """
            Uses LEAKY_BUCKET algorithm — smoothest traffic.
            No bursting allowed. Requests processed at constant rate.
            """
    )
    @PostMapping("/upload")
    @RateLimit(
            limit = 5,
            windowSeconds = 60,
            algorithm = "LEAKY_BUCKET"
    )
    public ResponseEntity<Map<String, Object>> upload(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Upload - Leaky Bucket protection");
        response.put("ruleType", "ANNOTATION_RULE - LEAKY_BUCKET");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Tier demo endpoint",
            description = """
            Tests Per User Tier Rate Limiting.
            
            Add header `X-User-Tier` with value:
            - `FREE` → blocked after 10 requests/min
            - `PRO` → blocked after 100 requests/min
            - `ENTERPRISE` → blocked after 1000 requests/min
            - `INTERNAL` → never blocked
            
            Also add `X-User-Id` header to identify the user.
            """
    )
    @GetMapping("/free-endpoint")
    public ResponseEntity<Map<String, Object>> freeEndpoint(
            @Parameter(description = "User tier — FREE/PRO/ENTERPRISE/INTERNAL")
            @RequestHeader(value = "X-User-Tier", required = false)
            String tier) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Tier demo endpoint");
        response.put("yourTier", tier != null ? tier : "NOT PROVIDED");
        response.put("tierLimits", Map.of(
                "FREE", "10 req/min",
                "PRO", "100 req/min",
                "ENTERPRISE", "1000 req/min",
                "INTERNAL", "Unlimited"
        ));
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Token Bucket endpoint",
            description = "Uses TOKEN_BUCKET algorithm — allows bursting up to limit"
    )
    @PostMapping("/data")
    @RateLimit(
            limit = 10,
            windowSeconds = 60,
            algorithm = "TOKEN_BUCKET"
    )
    public ResponseEntity<Map<String, Object>> postData(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "POST received");
        response.put("ruleType", "ANNOTATION_RULE - TOKEN_BUCKET");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

}