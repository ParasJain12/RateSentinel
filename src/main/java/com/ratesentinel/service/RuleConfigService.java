package com.ratesentinel.service;

import com.ratesentinel.dto.RateLimitRuleDTO;
import com.ratesentinel.model.RateLimitRule;
import com.ratesentinel.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleConfigService {

    private final RateLimitRuleRepository ruleRepository;

    public List<RateLimitRuleDTO> getAllRules() {
        return ruleRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<RateLimitRuleDTO> getRuleById(Long id) {
        return ruleRepository.findById(id).map(this::toDTO);
    }

    public RateLimitRuleDTO createRule(RateLimitRuleDTO dto) {
        RateLimitRule rule = toEntity(dto);
        RateLimitRule saved = ruleRepository.save(rule);
        log.info("Created new rate limit rule: {}", saved.getRuleName());
        return toDTO(saved);
    }

    public Optional<RateLimitRuleDTO> updateRule(Long id, RateLimitRuleDTO dto) {
        return ruleRepository.findById(id).map(existing -> {
            existing.setRuleName(dto.getRuleName());
            existing.setEndpointPattern(dto.getEndpointPattern());
            existing.setHttpMethod(dto.getHttpMethod());
            existing.setLimitCount(dto.getLimitCount());
            existing.setWindowSeconds(dto.getWindowSeconds());
            existing.setAlgorithm(dto.getAlgorithm());
            existing.setIdentifierType(dto.getIdentifierType());
            existing.setUserTier(dto.getUserTier());
            existing.setIsActive(dto.getIsActive());
            RateLimitRule updated = ruleRepository.save(existing);
            log.info("Updated rate limit rule: {}", updated.getRuleName());
            return toDTO(updated);
        });
    }

    public boolean toggleRule(Long id) {
        return ruleRepository.findById(id).map(rule -> {
            rule.setIsActive(!rule.getIsActive());
            ruleRepository.save(rule);
            log.info("Toggled rule {} to {}", rule.getRuleName(), rule.getIsActive());
            return true;
        }).orElse(false);
    }

    public boolean deleteRule(Long id) {
        if (ruleRepository.existsById(id)) {
            ruleRepository.deleteById(id);
            log.info("Deleted rule with id: {}", id);
            return true;
        }
        return false;
    }

    private RateLimitRuleDTO toDTO(RateLimitRule rule) {
        return RateLimitRuleDTO.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .endpointPattern(rule.getEndpointPattern())
                .httpMethod(rule.getHttpMethod())
                .limitCount(rule.getLimitCount())
                .windowSeconds(rule.getWindowSeconds())
                .algorithm(rule.getAlgorithm())
                .identifierType(rule.getIdentifierType())
                .userTier(rule.getUserTier())
                .isActive(rule.getIsActive())
                .build();
    }

    private RateLimitRule toEntity(RateLimitRuleDTO dto) {
        return RateLimitRule.builder()
                .ruleName(dto.getRuleName())
                .endpointPattern(dto.getEndpointPattern())
                .httpMethod(dto.getHttpMethod())
                .limitCount(dto.getLimitCount())
                .windowSeconds(dto.getWindowSeconds())
                .algorithm(dto.getAlgorithm())
                .identifierType(dto.getIdentifierType())
                .userTier(dto.getUserTier())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
    }

}