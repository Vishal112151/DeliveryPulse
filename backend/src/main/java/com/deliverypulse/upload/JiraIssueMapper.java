package com.deliverypulse.upload;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.deliverypulse.shared.domain.IssueType;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns Jira issues into the same records a file upload produces, one project per Jira project.
 * Field mapping is documented in the README (section "Jira sync").
 */
final class JiraIssueMapper {

    private JiraIssueMapper() {
    }

    static ImportDocument toDocument(List<JsonNode> issues, JiraProperties properties) {
        List<ImportIssue> warnings = new ArrayList<>();
        List<JsonNode> kept = issues;
        if (issues.size() > properties.maxIssues()) {
            kept = issues.subList(0, properties.maxIssues());
            warnings.add(new ImportIssue("Jira", null, null, "JIRA_ISSUE_LIMIT",
                    "Only the first " + properties.maxIssues() + " issues were synced (JIRA_MAX_ISSUES)."));
        }

        Map<String, List<JsonNode>> byProject = new LinkedHashMap<>();
        Map<String, String> projectNames = new LinkedHashMap<>();
        for (JsonNode issue : kept) {
            JsonNode project = issue.path("fields").path("project");
            String key = project.path("key").asText("").toUpperCase(Locale.ROOT);
            byProject.computeIfAbsent(key, ignored -> new ArrayList<>()).add(issue);
            projectNames.putIfAbsent(key, limit(project.path("name").asText(key), 160));
        }
        for (String key : properties.projectKeys()) {
            if (!byProject.containsKey(key)) {
                warnings.add(new ImportIssue(key, null, null, "JIRA_PROJECT_EMPTY",
                        "Jira returned no issues for project " + key + "; it was not imported."));
            }
        }

        List<ImportProject> projects = new ArrayList<>();
        byProject.forEach((key, projectIssues) -> {
            Set<String> keysInProject = projectIssues.stream()
                    .map(issue -> issue.path("key").asText().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            List<ImportedTask> tasks = new ArrayList<>();
            List<ImportedDependency> dependencies = new ArrayList<>();
            for (int index = 0; index < projectIssues.size(); index++) {
                JsonNode issue = projectIssues.get(index);
                String issueKey = issue.path("key").asText();
                List<String> blockers = blockedBy(issue.path("fields"));
                tasks.add(task(index + 1, issueKey, issue.path("fields"), blockers, properties, warnings));
                blockers.stream()
                        .filter(blocker -> keysInProject.contains(blocker.toUpperCase(Locale.ROOT)))
                        .forEach(blocker -> dependencies.add(new ImportedDependency(issueKey, blocker, blocker)));
            }
            projects.add(new ImportProject(projectNames.get(key), tasks, dependencies));
        });
        return new ImportDocument(projects, List.of(), warnings, List.of());
    }

    private static ImportedTask task(int row, String key, JsonNode fields, List<String> blockers,
            JiraProperties properties, List<ImportIssue> warnings) {
        JsonNode milestone = milestone(fields.path("fixVersions"));
        return new ImportedTask(row, key,
                limit(fields.path("summary").asText(key), 300),
                limit(text(fields.path("components").path(0).path("name")), 160),
                limit(text(fields.path("assignee").path("displayName")), 160),
                priority(fields.path("priority").path("name").asText(""), key, warnings),
                status(fields, properties.blockedLabel()),
                null,
                date(fields.path("duedate")),
                blockers.isEmpty() ? null : String.join(", ", blockers),
                null,
                isDefect(fields.path("issuetype").path("name").asText("")) ? IssueType.DEFECT : IssueType.TASK,
                milestone == null ? null : limit(text(milestone.path("name")), 160),
                milestone == null ? null : date(milestone.path("releaseDate")));
    }

    /** Done always wins; otherwise the blocked label or a status named "Blocked"; otherwise the status category. */
    static TaskStatus status(JsonNode fields, String blockedLabel) {
        JsonNode status = fields.path("status");
        String category = status.path("statusCategory").path("key").asText("");
        if (category.equals("done")) {
            return TaskStatus.DONE;
        }
        boolean labelled = StreamSupport.stream(fields.path("labels").spliterator(), false)
                .anyMatch(label -> label.asText().equalsIgnoreCase(blockedLabel));
        if (labelled || status.path("name").asText("").equalsIgnoreCase("blocked")) {
            return TaskStatus.BLOCKED;
        }
        return category.equals("indeterminate") ? TaskStatus.IN_PROGRESS : TaskStatus.NOT_STARTED;
    }

    private static Priority priority(String name, String key, List<ImportIssue> warnings) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "highest", "blocker", "critical" -> Priority.CRITICAL;
            case "high", "major" -> Priority.HIGH;
            case "medium", "" -> Priority.MEDIUM;
            case "low", "lowest", "minor", "trivial" -> Priority.LOW;
            default -> {
                warnings.add(new ImportIssue(key, null, "Priority", "JIRA_UNKNOWN_PRIORITY",
                        "Jira priority '" + name + "' is not recognised; Medium was used."));
                yield Priority.MEDIUM;
            }
        };
    }

    private static boolean isDefect(String issueType) {
        String type = issueType.toLowerCase(Locale.ROOT);
        return type.equals("bug") || type.equals("defect");
    }

    /** Keys of issues that block this one: "Blocks" links where the other issue is on the inward side. */
    private static List<String> blockedBy(JsonNode fields) {
        List<String> blockers = new ArrayList<>();
        for (JsonNode link : fields.path("issuelinks")) {
            String blocker = link.path("inwardIssue").path("key").asText("");
            if (link.path("type").path("name").asText("").equalsIgnoreCase("Blocks") && !blocker.isBlank()
                    && blockers.stream().noneMatch(blocker::equalsIgnoreCase)) {
                blockers.add(blocker);
            }
        }
        return blockers;
    }

    /** The fix version with the earliest release date (undated ones last). */
    private static JsonNode milestone(JsonNode fixVersions) {
        return StreamSupport.stream(fixVersions.spliterator(), false)
                .min(Comparator.comparing((JsonNode version) -> date(version.path("releaseDate")),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static LocalDate date(JsonNode node) {
        String value = text(node);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText().trim();
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
