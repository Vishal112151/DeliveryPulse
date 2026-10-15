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
        model = model == null || model.isBlank() ? "claude-opus-5-5" : model.trim();
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        maxOutputTokens = maxOutputTokens <= 0 ? 16000 : maxOutputTokens;
        maxTasks = maxTasks <= 0 ? 200 : maxTasks;
        maxRisks = maxRisks <= 0 ? 10 : maxRisks;
        maxExplainedRisks = maxExplainedRisks <= 0 ? 5 : Math.min(maxExplainedRisks, maxRisks);
        maxTextLength = maxTextLength <= 0 ? 500 : maxTextLength;
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
