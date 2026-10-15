package com.deliverypulse.insights.llm;

/** A system instruction plus one user message. The user message carries data, never operator instructions. */
public record LlmPrompt(String system, String user) {
}
