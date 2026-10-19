package com.deliverypulse.upload;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional Jira Cloud connection. Credentials come only from the environment (see application.yml)
 * and are never returned by the API or logged.
 */
@ConfigurationProperties(prefix = "app.jira")
public record JiraProperties(
        String baseUrl,
        String email,
        String apiToken,
        List<String> projectKeys,
        String blockedLabel,
        Duration timeout,
        int maxIssues) {

    public JiraProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        projectKeys = projectKeys == null ? List.of() : projectKeys.stream()
                .map(key -> key.trim().toUpperCase(Locale.ROOT))
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        blockedLabel = blockedLabel == null || blockedLabel.isBlank() ? "blocked" : blockedLabel.trim();
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        maxIssues = maxIssues <= 0 ? 5000 : maxIssues;
    }

    public boolean configured() {
        return !baseUrl.isBlank() && email != null && !email.isBlank()
                && apiToken != null && !apiToken.isBlank() && !projectKeys.isEmpty();
    }

    /** Never expose the token through toString (records print every component by default). */
    @Override
    public String toString() {
        return "JiraProperties[baseUrl=" + baseUrl + ", projectKeys=" + projectKeys
                + ", apiToken=" + (apiToken == null || apiToken.isBlank() ? "<unset>" : "<set>") + "]";
    }
}
