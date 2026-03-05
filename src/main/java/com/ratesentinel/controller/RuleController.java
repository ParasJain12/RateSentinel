package com.ratesentinel.controller;

import com.ratesentinel.dto.RateLimitRuleDTO;
import com.ratesentinel.model.BlackListEntry;
import com.ratesentinel.model.WhiteListEntry;
import com.ratesentinel.repository.BlacklistRepository;
import com.ratesentinel.repository.WhitelistRepository;
import com.ratesentinel.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleConfigService ruleConfigService;
    private final WhitelistRepository whitelistRepository;
    private final BlacklistRepository blacklistRepository;

    // Get all rules
    @GetMapping
    public ResponseEntity<List<RateLimitRuleDTO>> getAllRules() {
        return ResponseEntity.ok(ruleConfigService.getAllRules());
    }

    // Get rule by id
    @GetMapping("/{id}")
    public ResponseEntity<RateLimitRuleDTO> getRuleById(@PathVariable Long id) {
        return ruleConfigService.getRuleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Create new rule
    @PostMapping
    public ResponseEntity<RateLimitRuleDTO> createRule(
            @RequestBody RateLimitRuleDTO dto) {
        return ResponseEntity.ok(ruleConfigService.createRule(dto));
    }

    // Update rule
    @PutMapping("/{id}")
    public ResponseEntity<RateLimitRuleDTO> updateRule(
            @PathVariable Long id,
            @RequestBody RateLimitRuleDTO dto) {
        return ruleConfigService.updateRule(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Toggle rule on/off
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable Long id) {
        boolean success = ruleConfigService.toggleRule(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Rule toggled successfully" : "Rule not found"
        ));
    }

    // Delete rule
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable Long id) {
        boolean deleted = ruleConfigService.deleteRule(id);
        return ResponseEntity.ok(Map.of(
                "deleted", deleted,
                "message", deleted ? "Rule deleted" : "Rule not found"
        ));
    }

    // Add IP to whitelist
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
                .ipAddress(ip)
                .reason(reason)
                .build();
        whitelistRepository.save(entry);
        return ResponseEntity.ok(Map.of(
                "message", "IP added to whitelist", "ip", ip));
    }

    // Add IP to blacklist
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
                .ipAddress(ip)
                .reason(reason)
                .build();
        blacklistRepository.save(entry);
        return ResponseEntity.ok(Map.of(
                "message", "IP added to blacklist", "ip", ip));
    }

    // Get whitelist
    @GetMapping("/whitelist")
    public ResponseEntity<List<WhiteListEntry>> getWhitelist() {
        return ResponseEntity.ok(whitelistRepository.findAll());
    }

    // Get blacklist
    @GetMapping("/blacklist")
    public ResponseEntity<List<BlackListEntry>> getBlacklist() {
        return ResponseEntity.ok(blacklistRepository.findAll());
    }

}