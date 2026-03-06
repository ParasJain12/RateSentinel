package com.ratesentinel.model;

public enum UserTier {
    FREE,
    PRO,
    ENTERPRISE,
    INTERNAL;

    public static UserTier fromString(String value) {
        if (value == null) return FREE;
        try {
            return UserTier.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Unknown tier defaults to FREE
            return FREE;
        }
    }
}