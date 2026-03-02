package com.ratesentinel.dto;

import com.ratesentinel.model.AlgorithmType;
import com.ratesentinel.model.IdentifierType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRuleDTO {

    private Long id;
    private String ruleName;
    private String endpointPattern;
    private String httpMethod;
    private Integer limitCount;
    private Integer windowSeconds;
    private AlgorithmType algorithm;
    private IdentifierType identifierType;
    private String userTier;
    private Boolean isActive;

}