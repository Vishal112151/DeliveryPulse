package com.deliverypulse.insights.llm;

/**
 * Provider-neutral boundary for structured text generation. Implementations translate provider
 * errors into {@link LlmException} with an {@link LlmFailure} so callers never depend on a vendor SDK.
 */
public interface LlmClient {

    String providerName();

    /** True when credentials are present and the provider can be called. */
    boolean isConfigured();

    /**
     * Generates a response constrained to the JSON shape of {@code responseType}.
     *
     * @throws LlmException for timeouts, rate limits, quota, unavailability, refusals or unparseable output
     */
    <T> LlmResult<T> generate(LlmPrompt prompt, Class<T> responseType);
}
