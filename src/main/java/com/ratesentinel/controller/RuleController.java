package com.ratesentinel.controller;

import com.ratesentinel.dto.RateLimitRuleDTO;
import com.ratesentinel.model.BlackListEntry;
import com.ratesentinel.model.WhiteListEntry;
import com.ratesentinel.repository.BlacklistRepository;
import com.ratesentinel.repository.WhitelistRepository;
import com.ratesentinel.service.RuleConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Tag(name = "Rate Limit Rules",
        description = "Manage rate limiting rules for endpoints")
public class RuleController {

    private final RuleConfigService ruleConfigService;
    private final WhitelistRepository whitelistRepository;
    private final BlacklistRepository blacklistRepository;

    @Operation(
            summary = "Get all rate limit rules",
            description = "Returns all rate limit rules including inactive ones"
    )
    @ApiResponse(responseCode = "200", description = "List of all rules")
    @GetMapping
    public ResponseEntity<List<RateLimitRuleDTO>> getAllRules() {
        return ResponseEntity.ok(ruleConfigService.getAllRules());
    }

    @Operation(
            summary = "Get rule by ID",
            description = "Returns a specific rate limit rule by its ID"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule found"),
            @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<RateLimitRuleDTO> getRuleById(
            @Parameter(description = "Rule ID", example = "1")
            @PathVariable Long id) {
        return ruleConfigService.getRuleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create a new rate limit rule",
            description = """
            Creates a new rate limit rule for an endpoint.
            
            **Algorithm Options:**
            - `FIXED_WINDOW` — Simple counter per time window
            - `SLIDING_WINDOW_LOG` — Precise sliding window using sorted set
            - `SLIDING_WINDOW_COUNTER` — Memory efficient sliding window
            - `TOKEN_BUCKET` — Allows bursting, refills over time
            - `LEAKY_BUCKET` — Smooth constant rate, no bursting
            
            **Identifier Type Options:**
            - `IP_ADDRESS` — Rate limit per client IP
            - `USER_ID` — Rate limit per authenticated user
            - `API_KEY` — Rate limit per API key
            """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    examples = @ExampleObject(
                            name = "Example Rule",
                            value = """
                    {
                        "ruleName": "login-protection",
                        "endpointPattern": "/api/login",
                        "httpMethod": "POST",
                        "limitCount": 5,
                        "windowSeconds": 60,
                        "algorithm": "FIXED_WINDOW",
                        "identifierType": "IP_ADDRESS",
                        "isActive": true
                    }
                    """
                    )
            )
    )
    @PostMapping
    public ResponseEntity<RateLimitRuleDTO> createRule(
            @RequestBody RateLimitRuleDTO dto) {
        return ResponseEntity.ok(ruleConfigService.createRule(dto));
    }

    @Operation(
            summary = "Update an existing rule",
            description = "Updates rule configuration. Cache is invalidated automatically."
    )
    @PutMapping("/{id}")
    public ResponseEntity<RateLimitRuleDTO> updateRule(
            @Parameter(description = "Rule ID", example = "1")
            @PathVariable Long id,
            @RequestBody RateLimitRuleDTO dto) {
        return ruleConfigService.updateRule(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Toggle rule on or off",
            description = "Enables or disables a rule instantly without deletion"
    )
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(
            @Parameter(description = "Rule ID", example = "1")
            @PathVariable Long id) {
        boolean success = ruleConfigService.toggleRule(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success
                        ? "Rule toggled successfully" : "Rule not found"
        ));
    }

    @Operation(
            summary = "Delete a rule",
            description = "Permanently deletes a rate limit rule"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRule(
            @Parameter(description = "Rule ID", example = "1")
            @PathVariable Long id) {
        boolean deleted = ruleConfigService.deleteRule(id);
        return ResponseEntity.ok(Map.of(
                "deleted", deleted,
                "message", deleted ? "Rule deleted" : "Rule not found"
        ));
    }

    @Operation(
            summary = "Add IP to whitelist",
            description = "Whitelisted IPs bypass ALL rate limiting completely"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                    {
                        "ipAddress": "192.168.1.100",
                        "reason": "Internal monitoring service"
                    }
                    """
                    )
            )
    )
    @PostMapping("/whitelist")
    public ResponseEntity<Map<String, Object>> addToWhitelist(
            @RequestBody Map<String, String> body) {
        String ip = body.get("ipAddress");
        String reason = body.get("reason");
        if (whitelistRepository.existsByIpAddress(ip)) {
            return ResponseEntity.ok(Map.of(
                    "message", "IP already whitelisted"));
        }
        WhiteListEntry entry = WhiteListEntry.builder()
                .ipAddress(ip).reason(reason).build();
        whitelistRepository.save(entry);
        return ResponseEntity.ok(Map.of(
                "message", "IP added to whitelist", "ip", ip));
    }

    @Operation(
            summary = "Add IP to blacklist",
            description = "Blacklisted IPs are blocked before rate limit check"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                    {
                        "ipAddress": "10.0.0.1",
                        "reason": "Suspicious activity detected"
                    }
                    """
                    )
            )
    )
    @PostMapping("/blacklist")
    public ResponseEntity<Map<String, Object>> addToBlacklist(
            @RequestBody Map<String, String> body) {
        String ip = body.get("ipAddress");
        String reason = body.get("reason");
        if (blacklistRepository.existsByIpAddress(ip)) {
            return ResponseEntity.ok(Map.of(
                    "message", "IP already blacklisted"));
        }
        BlackListEntry entry = BlackListEntry.builder()
                .ipAddress(ip).reason(reason).build();
        blacklistRepository.save(entry);
        return ResponseEntity.ok(Map.of(
                "message", "IP added to blacklist", "ip", ip));
    }

    @Operation(summary = "Get all whitelisted IPs")
    @GetMapping("/whitelist")
    public ResponseEntity<List<WhiteListEntry>> getWhitelist() {
        return ResponseEntity.ok(whitelistRepository.findAll());
    }

    @Operation(summary = "Get all blacklisted IPs")
    @GetMapping("/blacklist")
    public ResponseEntity<List<BlackListEntry>> getBlacklist() {
        return ResponseEntity.ok(blacklistRepository.findAll());
    }

}