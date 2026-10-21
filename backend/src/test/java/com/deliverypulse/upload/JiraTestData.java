package com.deliverypulse.upload;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A Jira Cloud /rest/api/3/search/jql page, trimmed to the fields DeliveryPulse requests. */
final class JiraTestData {

    static final String SEARCH_PAGE = """
            {"isLast": true, "issues": [
              {"key": "DPDEMO-1", "fields": {
                "project": {"key": "DPDEMO", "name": "DeliveryPulse Demo"},
                "summary": "Checkout API", "issuetype": {"name": "Story"}, "priority": {"name": "High"},
                "description": {"type": "doc", "version": 1, "content": [
                  {"type": "paragraph", "content": [{"type": "text", "text": "Expose checkout to "},
                                                    {"type": "text", "text": "partners", "marks": [{"type": "strong"}]},
                                                    {"type": "text", "text": "."}]},
                  {"type": "bulletList", "content": [
                    {"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "Idempotent"}]}]},
                    {"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "Rate limited"}]}]}]}]},
                "status": {"name": "In Progress", "statusCategory": {"key": "indeterminate"}},
                "assignee": {"displayName": "Asha"}, "duedate": "2026-10-15",
                "components": [{"name": "Backend"}], "labels": [],
                "fixVersions": [{"name": "Alpha", "releaseDate": "2026-10-16"}], "issuelinks": []}},
              {"key": "DPDEMO-2", "fields": {
                "project": {"key": "DPDEMO", "name": "DeliveryPulse Demo"},
                "summary": "Payment provider setup", "issuetype": {"name": "Task"}, "priority": {"name": "High"},
                "status": {"name": "In Progress", "statusCategory": {"key": "indeterminate"}},
                "assignee": {"displayName": "Ravi"}, "duedate": "2026-10-25",
                "components": [], "labels": ["Blocked"],
                "fixVersions": [{"name": "GA", "releaseDate": "2026-12-15"}, {"name": "Beta", "releaseDate": "2026-10-30"}],
                "issuelinks": [{"type": {"name": "Blocks", "inward": "is blocked by", "outward": "blocks"},
                                "outwardIssue": {"key": "DPDEMO-5"}}]}},
              {"key": "DPDEMO-3", "fields": {
                "project": {"key": "DPDEMO", "name": "DeliveryPulse Demo"},
                "summary": "Login crash on Android", "issuetype": {"name": "Bug"}, "priority": {"name": "Highest"},
                "status": {"name": "Done", "statusCategory": {"key": "done"}},
                "assignee": {"displayName": "Meera"}, "duedate": null,
                "components": [{"name": "Mobile"}], "labels": ["blocked"], "fixVersions": [], "issuelinks": []}},
              {"key": "DPDEMO-5", "fields": {
                "project": {"key": "DPDEMO", "name": "DeliveryPulse Demo"},
                "summary": "Load testing", "issuetype": {"name": "Task"}, "priority": {"name": "Medium"},
                "status": {"name": "To Do", "statusCategory": {"key": "new"}},
                "assignee": {"displayName": "Sara"}, "duedate": "2026-12-01",
                "components": [{"name": "QA"}], "labels": [], "fixVersions": [{"name": "GA", "releaseDate": "2026-12-15"}],
                "issuelinks": [
                  {"type": {"name": "Blocks", "inward": "is blocked by", "outward": "blocks"}, "inwardIssue": {"key": "DPDEMO-2"}},
                  {"type": {"name": "Blocks", "inward": "is blocked by", "outward": "blocks"}, "inwardIssue": {"key": "OTHER-9"}},
                  {"type": {"name": "Relates", "inward": "relates to", "outward": "relates to"}, "inwardIssue": {"key": "DPDEMO-1"}}]}},
              {"key": "DPDEMO-6", "fields": {
                "project": {"key": "DPDEMO", "name": "DeliveryPulse Demo"},
                "summary": "Release notes", "issuetype": {"name": "Task"}, "priority": {"name": "Weird"},
                "status": {"name": "Blocked", "statusCategory": {"key": "indeterminate"}},
                "assignee": null, "duedate": "2026-12-10", "components": [], "labels": [], "fixVersions": [],
                "issuelinks": []}}
            ]}
            """;

    private JiraTestData() {
    }

    static JiraProperties properties(String... projectKeys) {
        return new JiraProperties("https://example.atlassian.net/", "me@example.com", "secret-token",
                List.of(projectKeys), null, Duration.ofSeconds(5), 0);
    }

    static List<JsonNode> issues() throws Exception {
        List<JsonNode> issues = new ArrayList<>();
        new ObjectMapper().readTree(SEARCH_PAGE).path("issues").forEach(issues::add);
        return issues;
    }
}
