package com.ratesentinel.controller;

import com.ratesentinel.model.TierConfig;
import com.ratesentinel.repository.TierConfigRepository;
import com.ratesentinel.service.TierRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tiers")
@RequiredArgsConstructor
@Tag(name = "Tier Management",
        description = "Manage user tier rate limit configurations")
public class TierController {

    private final TierConfigRepository tierConfigRepository;
    private final TierRateLimitService tierRateLimitService;

    @Operation(
            summary = "Get all tier configurations",
            description = """
            Returns all tier configurations.
            
            To use tiers, add header to your requests:
            `X-User-Tier: FREE` or `PRO` or `ENTERPRISE` or `INTERNAL`
            
            **Default Limits:**
            - FREE → 10 requests/minute
            - PRO → 100 requests/minute
            - ENTERPRISE → 1000 requests/minute
            - INTERNAL → Unlimited
            """
    )
    @GetMapping
    public ResponseEntity<List<TierConfig>> getAllTiers() {
        return ResponseEntity.ok(tierConfigRepository.findAll());
    }

    @Operation(
            summary = "Update tier limit",
            description = "Update rate limit for a specific tier. Cache invalidated automatically."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    examples = @ExampleObject(
                            name = "Update FREE tier",
                            value = """
                    {
                        "limitCount": 20,
                        "windowSeconds": 60
                    }
                    """
                    )
            )
    )
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