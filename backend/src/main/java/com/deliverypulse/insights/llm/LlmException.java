package com.deliverypulse.insights.llm;

public class LlmException extends RuntimeException {

    private final LlmFailure failure;

    public LlmException(LlmFailure failure, String message) {
        this(failure, message, null);
    }

    public LlmException(LlmFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public LlmFailure getFailure() {
        return failure;
    }
}
