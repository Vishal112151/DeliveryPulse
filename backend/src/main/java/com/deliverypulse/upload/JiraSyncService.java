package com.deliverypulse.upload;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

/** Pulls the configured Jira projects and imports them with "update existing projects" behaviour. */
@Service
public class JiraSyncService {

    private static final Logger logger = LoggerFactory.getLogger(JiraSyncService.class);

    private final JiraProperties properties;
    private final JiraClient client;
    private final ImportPersistenceService persistenceService;

    public JiraSyncService(JiraProperties properties, JiraClient client, ImportPersistenceService persistenceService) {
        this.properties = properties;
        this.client = client;
        this.persistenceService = persistenceService;
    }

    public JiraStatusResponse status() {
        return new JiraStatusResponse(properties.configured(),
                properties.baseUrl().isBlank() ? null : properties.baseUrl(),
                properties.projectKeys(), properties.blockedLabel());
    }

    public ImportSummary sync() {
        if (!properties.configured()) {
            throw new UploadRejectedException(HttpStatus.BAD_REQUEST, "Jira sync is not configured. Set JIRA_BASE_URL, "
                    + "JIRA_EMAIL, JIRA_API_TOKEN and JIRA_PROJECT_KEYS in .env and restart the backend.");
        }
        List<JsonNode> issues = client.fetchIssues();
        ImportDocument document = JiraIssueMapper.toDocument(issues, properties);
        String source = "Jira " + String.join(",", properties.projectKeys());
        ImportSummary summary = persistenceService.persist(source.length() > 255 ? source.substring(0, 255) : source,
                "jira", document, true);
        logger.info("Jira sync completed: projects={}, tasks={}, warnings={}",
                summary.projectCount(), summary.importedTaskCount(), summary.warnings().size());
        return summary;
    }

    public record JiraStatusResponse(boolean configured, String baseUrl, List<String> projectKeys, String blockedLabel) {
    }
}
