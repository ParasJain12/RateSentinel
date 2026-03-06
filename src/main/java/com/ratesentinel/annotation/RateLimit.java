package com.ratesentinel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    int limit() default 100;

    int windowSeconds() default 60;

    // Now includes all 5 algorithms
    // Options: FIXED_WINDOW, SLIDING_WINDOW_LOG, SLIDING_WINDOW_COUNTER,
    //          TOKEN_BUCKET, LEAKY_BUCKET
    String algorithm() default "SLIDING_WINDOW_COUNTER";

    String identifierType() default "IP_ADDRESS";

    String key() default "";

    String message() default "Rate limit exceeded. Please slow down.";

}