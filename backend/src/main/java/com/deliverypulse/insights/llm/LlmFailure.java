package com.deliverypulse.insights.llm;

/** Provider-neutral failure categories surfaced to callers. */
public enum LlmFailure {
    NOT_CONFIGURED,
    TIMEOUT,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    AUTHENTICATION_FAILED,
    UNAVAILABLE,
    REFUSED,
    INVALID_RESPONSE
}
