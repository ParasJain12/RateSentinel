package com.ratesentinel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ratesentinel.model.RateLimitRule;

@Repository
public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule,Long> {

	// Find rule by endpoint pattern and http method
    Optional<RateLimitRule> findByEndpointPatternAndHttpMethod(
        String endpointPattern, String httpMethod);

    // Find all active rules
    List<RateLimitRule> findByIsActiveTrue();

    // Find active rules by endpoint pattern
    List<RateLimitRule> findByEndpointPatternAndIsActiveTrue(String endpointPattern);

    // Find rule by name
    Optional<RateLimitRule> findByRuleName(String ruleName);

    // Find rules by user tier
    List<RateLimitRule> findByUserTierAndIsActiveTrue(String userTier);
}
