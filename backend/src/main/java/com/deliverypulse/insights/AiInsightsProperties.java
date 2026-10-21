package com.deliverypulse.insights;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the optional AI insights module. Credentials come only from the environment
 * (see application.yml); nothing here is ever written to source control or logs.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiInsightsProperties(
        boolean enabled,
        String provider,
        String apiKey,
        String model,
        Duration timeout,
        int maxRetries,
        long maxOutputTokens,
        boolean serverSideFallbacks,
        boolean externalDataSharingAuthorized,
        boolean includeAssigneeNames,
        boolean includeTaskDescriptions,
        int maxTasks,
        int maxRisks,
        int maxExplainedRisks,
        int maxTextLength) {

    public AiInsightsProperties {
        provider = provider == null || provider.isBlank() ? "anthropic" : provider.trim();
        model = model == null || model.isBlank() ? defaultModel(provider) : model.trim();
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        maxOutputTokens = maxOutputTokens <= 0 ? 16000 : maxOutputTokens;
        maxTasks = maxTasks <= 0 ? 200 : maxTasks;
        maxRisks = maxRisks <= 0 ? 10 : maxRisks;
        maxExplainedRisks = maxExplainedRisks <= 0 ? 5 : Math.min(maxExplainedRisks, maxRisks);
        maxTextLength = maxTextLength <= 0 ? 500 : maxTextLength;
    }

    /** Google's alias for its current Flash model; pinned versions get retired for new accounts. */
    static final String DEFAULT_GEMINI_MODEL = "gemini-flash-latest";

    static final String DEFAULT_GROQ_MODEL = "openai/gpt-oss-120b";

    private static String defaultModel(String provider) {
        if (provider.equalsIgnoreCase("gemini")) {
            return DEFAULT_GEMINI_MODEL;
        }
        return provider.equalsIgnoreCase("groq") ? DEFAULT_GROQ_MODEL : "claude-opus-5-5";
    }

    /** The same limits and privacy settings for a second provider; a blank model picks that provider's default. */
    public AiInsightsProperties forProvider(String otherProvider, String otherApiKey, String otherModel) {
        return new AiInsightsProperties(enabled, otherProvider, otherApiKey, otherModel, timeout, maxRetries,
                maxOutputTokens, serverSideFallbacks, externalDataSharingAuthorized, includeAssigneeNames,
                includeTaskDescriptions, maxTasks, maxRisks, maxExplainedRisks, maxTextLength);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never expose the key through toString (records print every component by default). */
    @Override
    public String toString() {
        return "AiInsightsProperties[enabled=" + enabled + ", provider=" + provider + ", model=" + model
                + ", apiKey=" + (hasApiKey() ? "<set>" : "<unset>")
                + ", externalDataSharingAuthorized=" + externalDataSharingAuthorized + "]";
    }
}
