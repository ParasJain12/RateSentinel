package com.ratesentinel.controller;

import com.ratesentinel.model.TierConfig;
import com.ratesentinel.repository.TierConfigRepository;
import com.ratesentinel.service.TierRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tiers")
@RequiredArgsConstructor
public class TierController {

    private final TierConfigRepository tierConfigRepository;
    private final TierRateLimitService tierRateLimitService;

    // Get all tier configurations
    @GetMapping
    public ResponseEntity<List<TierConfig>> getAllTiers() {
        return ResponseEntity.ok(tierConfigRepository.findAll());
    }

    // Update tier limit
    @PutMapping("/{tierName}")
    public ResponseEntity<?> updateTierLimit(
            @PathVariable String tierName,
            @RequestBody Map<String, Object> body) {

        Optional<TierConfig> optional = tierConfigRepository
                .findByTierNameAndIsActiveTrue(tierName.toUpperCase());

        if (optional.isEmpty()) {
            return ResponseEntity.ok(
                    Map.of("message", "Tier not found: " + tierName));
        }

        TierConfig config = optional.get();

        if (body.containsKey("limitCount")) {
            config.setLimitCount((Integer) body.get("limitCount"));
        }
        if (body.containsKey("windowSeconds")) {
            config.setWindowSeconds((Integer) body.get("windowSeconds"));
        }

        tierConfigRepository.save(config);
        tierRateLimitService.invalidateTierCache(tierName.toUpperCase());

        return ResponseEntity.ok(Map.of(
                "message", "Tier updated successfully",
                "tier", tierName,
                "newLimit", config.getLimitCount()
        ));
    }

}