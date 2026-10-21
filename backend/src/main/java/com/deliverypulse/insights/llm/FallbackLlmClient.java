package com.deliverypulse.insights.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries {@code primary} first and, on any provider failure (quota, rate limit, timeout, outage, refusal,
 * unusable output), the same request on {@code secondary}. The secondary's failure is the one reported.
 */
public record FallbackLlmClient(LlmClient primary, LlmClient secondary) implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(FallbackLlmClient.class);

    @Override
    public String providerName() {
        return primary.providerName() + ", with " + secondary.providerName() + " as fallback";
    }

    @Override
    public boolean isConfigured() {
        return primary.isConfigured() || secondary.isConfigured();
    }

    @Override
    public <T> LlmResult<T> generate(LlmPrompt prompt, Class<T> responseType) {
        if (primary.isConfigured()) {
            try {
                return primary.generate(prompt, responseType);
            } catch (LlmException exception) {
                if (!secondary.isConfigured()) {
                    throw exception;
                }
                logger.warn("AI provider {} failed ({}); trying {}", primary.providerName(), exception.getFailure(),
                        secondary.providerName());
            }
        }
        return secondary.generate(prompt, responseType);
    }
}
