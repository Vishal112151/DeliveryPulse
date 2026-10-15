package com.deliverypulse.insights.llm;

/** A parsed provider response plus the metadata needed for logging and cost tracking. */
public record LlmResult<T>(T content, String model, long inputTokens, long outputTokens) {
}
