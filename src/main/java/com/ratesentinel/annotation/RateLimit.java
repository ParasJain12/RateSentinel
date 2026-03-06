package com.ratesentinel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply rate limiting directly on any controller method
 *
 * Usage:
 * @RateLimit(limit = 5, windowSeconds = 60)
 * @RateLimit(limit = 100, windowSeconds = 60, algorithm = "TOKEN_BUCKET")
 * @RateLimit(limit = 3, windowSeconds = 60, identifierType = "USER_ID")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    // Maximum requests allowed in the window
    int limit() default 100;

    // Time window in seconds
    int windowSeconds() default 60;

    // Algorithm to use
    // Options: FIXED_WINDOW, SLIDING_WINDOW_LOG, SLIDING_WINDOW_COUNTER,
    //          TOKEN_BUCKET
    String algorithm() default "SLIDING_WINDOW_COUNTER";

    // What to identify the client by
    // Options: IP_ADDRESS, USER_ID, API_KEY
    String identifierType() default "IP_ADDRESS";

    // Optional key suffix to differentiate same endpoint different rules
    String key() default "";

    // Custom message when rate limit is exceeded
    String message() default "Rate limit exceeded. Please slow down.";

}