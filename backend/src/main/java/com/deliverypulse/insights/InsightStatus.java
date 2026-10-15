package com.deliverypulse.insights;

/** Outcome of an insights request. Anything other than GENERATED is served by the deterministic fallback. */
public enum InsightStatus {
    GENERATED,
    DISABLED,
    NOT_CONFIGURED,
    NOT_AUTHORIZED,
    ANALYSIS_REQUIRED,
    TIMEOUT,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    AUTHENTICATION_FAILED,
    UNAVAILABLE,
    REFUSED,
    INVALID_RESPONSE
}
