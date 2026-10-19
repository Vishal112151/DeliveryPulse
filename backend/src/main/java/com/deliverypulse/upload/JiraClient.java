package com.deliverypulse.upload;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

/** Reads issues from Jira Cloud. It never writes to Jira. */
@Component
@EnableConfigurationProperties(JiraProperties.class)
public class JiraClient {

    static final String FIELDS = "summary,issuetype,priority,status,assignee,duedate,components,labels,"
            + "fixVersions,issuelinks,project";
    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Z][A-Z0-9_]{0,49}");
    private static final int PAGE_SIZE = 100;

    private final JiraProperties properties;
    private final RestClient restClient;

    @Autowired
    public JiraClient(JiraProperties properties) {
        this(properties, RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build());
    }

    JiraClient(JiraProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** All issues of the configured projects, in key order. Returns at most maxIssues + 1 so callers can warn. */
    List<JsonNode> fetchIssues() {
        for (String key : properties.projectKeys()) {
            if (!PROJECT_KEY.matcher(key).matches()) {
                throw new UploadRejectedException(HttpStatus.BAD_REQUEST,
                        "JIRA_PROJECT_KEYS contains an invalid project key: " + key);
            }
        }
        String jql = "project in (" + String.join(", ", properties.projectKeys()) + ") ORDER BY key";
        List<JsonNode> issues = new ArrayList<>();
        String nextPageToken = null;
        do {
            JsonNode page = get(searchUri(jql, nextPageToken));
            page.path("issues").forEach(issues::add);
            nextPageToken = page.path("isLast").asBoolean(true) ? null : page.path("nextPageToken").asText(null);
        } while (nextPageToken != null && issues.size() <= properties.maxIssues());
        return issues;
    }

    private URI searchUri(String jql, String nextPageToken) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("fields", FIELDS)
                .queryParam("maxResults", PAGE_SIZE);
        if (nextPageToken != null) {
            uri.queryParam("nextPageToken", nextPageToken);
        }
        return uri.encode().build().toUri();
    }

    private JsonNode get(URI uri) {
        try {
            JsonNode body = restClient.get().uri(uri)
                    .headers(headers -> headers.setBasicAuth(properties.email(), properties.apiToken()))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new UploadRejectedException(HttpStatus.BAD_GATEWAY, "Jira returned an empty response.");
            }
            return body;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String message = switch (status) {
                case 401, 403 -> "Jira rejected the credentials. Check JIRA_EMAIL and JIRA_API_TOKEN.";
                case 400 -> "Jira rejected the search. Check that every key in JIRA_PROJECT_KEYS is a project you can see.";
                case 404 -> "Jira search API not found. Check JIRA_BASE_URL (for example https://yourname.atlassian.net).";
                case 429 -> "Jira is rate limiting requests. Try again in a minute.";
                default -> "Jira returned HTTP " + status + ".";
            };
            throw new UploadRejectedException(HttpStatus.BAD_GATEWAY, message);
        } catch (ResourceAccessException exception) {
            throw new UploadRejectedException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Jira at " + properties.baseUrl() + ".");
        }
    }

    private static JdkClientHttpRequestFactory requestFactory(JiraProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }
}
