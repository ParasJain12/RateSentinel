package com.ratesentinel.annotation;

import com.ratesentinel.algorithm.*;
import com.ratesentinel.exception.RateLimitExceededException;
import com.ratesentinel.model.AlgorithmType;
import com.ratesentinel.model.IdentifierType;
import com.ratesentinel.model.RateLimitRule;
import com.ratesentinel.service.RedisCircuitBreakerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final AlgorithmFactory algorithmFactory;

    /**
     * Intercept any method annotated with @RateLimit
     * This runs AROUND the method — before and after
     */
    @Around("@annotation(com.ratesentinel.annotation.RateLimit)")
    public Object applyRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {

        // Step 1 — Get the annotation from the method
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimitAnnotation = method.getAnnotation(RateLimit.class);

        // Step 2 — Get current HTTP request
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            // Not an HTTP context, skip rate limiting
            return joinPoint.proceed();
        }

        // Step 3 — Extract identifier
        String identifier = resolveIdentifier(
                rateLimitAnnotation.identifierType(), request);

        // Step 4 — Build key
        // Uses method name + optional custom key so each annotated method
        // has its own independent counter in Redis
        String endpoint = buildEndpointKey(joinPoint, rateLimitAnnotation);
        String httpMethod = request.getMethod();

        // Step 5 — Build a RateLimitRule from annotation values
        // We don't need a database entry — annotation IS the rule
        RateLimitRule rule = buildRuleFromAnnotation(
                rateLimitAnnotation, endpoint, httpMethod);

        // Step 6 — Get algorithm and evaluate
        RateLimitAlgorithm algorithm = algorithmFactory.getAlgorithm(
                rule.getAlgorithm());
        RateLimitResult result = algorithm.isAllowed(identifier, rule);

        log.debug("@RateLimit check - Method: {}, Identifier: {}, Allowed: {}",
                method.getName(), identifier, result.isAllowed());

        // Step 7 — If blocked, throw exception
        if (!result.isAllowed()) {
            log.warn("@RateLimit BLOCKED - Method: {}, Identifier: {}",
                    method.getName(), identifier);
            throw new RateLimitExceededException(result, identifier, endpoint);
        }

        // Step 8 — Add headers to response
        addRateLimitHeaders(request, result);

        // Step 9 — Proceed with actual method execution
        return joinPoint.proceed();
    }

    /**
     * Build a RateLimitRule object directly from annotation values
     * This is the key insight — annotation values become the rule
     * No database needed
     */
    private RateLimitRule buildRuleFromAnnotation(RateLimit annotation,
                                                  String endpoint,
                                                  String httpMethod) {
        return RateLimitRule.builder()
                .endpointPattern(endpoint)
                .httpMethod(httpMethod)
                .limitCount(annotation.limit())
                .windowSeconds(annotation.windowSeconds())
                .algorithm(AlgorithmType.valueOf(annotation.algorithm()))
                .identifierType(IdentifierType.valueOf(annotation.identifierType()))
                .isActive(true)
                .build();
    }

    /**
     * Build unique endpoint key for Redis
     * ClassName.methodName ensures no two methods share a counter
     */
    private String buildEndpointKey(ProceedingJoinPoint joinPoint,
                                    RateLimit annotation) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        if (!annotation.key().isEmpty()) {
            return className + "." + methodName + "." + annotation.key();
        }

        return className + "." + methodName;
    }

    private String resolveIdentifier(String identifierType,
                                     HttpServletRequest request) {
        return switch (identifierType) {
            case "USER_ID" -> {
                String userId = request.getHeader("X-User-Id");
                yield userId != null ? userId : extractIpAddress(request);
            }
            case "API_KEY" -> {
                String apiKey = request.getHeader("X-Api-Key");
                yield apiKey != null ? apiKey : extractIpAddress(request);
            }
            default -> extractIpAddress(request);
        };
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder
                            .currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private void addRateLimitHeaders(HttpServletRequest request,
                                     RateLimitResult result) {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder
                            .currentRequestAttributes();
            var response = attributes.getResponse();
            if (response != null) {
                response.setHeader("X-RateLimit-Limit",
                        String.valueOf(result.getTotalLimit()));
                response.setHeader("X-RateLimit-Remaining",
                        String.valueOf(result.getRemainingRequests()));
                response.setHeader("X-RateLimit-Reset",
                        String.valueOf(result.getResetTimeSeconds()));
            }
        } catch (Exception e) {
            log.debug("Could not add rate limit headers: {}", e.getMessage());
        }
    }

}