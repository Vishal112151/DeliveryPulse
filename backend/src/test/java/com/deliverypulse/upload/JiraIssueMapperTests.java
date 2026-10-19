package com.deliverypulse.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.deliverypulse.shared.domain.IssueType;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;

class JiraIssueMapperTests {

    @Test
    void mapsJiraFieldsOntoImportedTasks() throws Exception {
        ImportDocument document = JiraIssueMapper.toDocument(JiraTestData.issues(), JiraTestData.properties("DPDEMO"));

        assertThat(document.errors()).isEmpty();
        ImportProject project = document.projects().getFirst();
        assertThat(project.name()).isEqualTo("DeliveryPulse Demo");
        Map<String, ImportedTask> tasks = project.tasks().stream()
                .collect(Collectors.toMap(ImportedTask::taskKey, Function.identity()));

        ImportedTask checkout = tasks.get("DPDEMO-1");
        assertThat(checkout.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(checkout.priority()).isEqualTo(Priority.HIGH);
        assertThat(checkout.teamOrModule()).isEqualTo("Backend");
        assertThat(checkout.milestone()).isEqualTo("Alpha");
        assertThat(checkout.milestoneDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(checkout.dueDate()).isEqualTo(LocalDate.of(2026, 10, 15));

        // Blocked label (any case) wins over In Progress; the earliest-dated fix version is the milestone.
        assertThat(tasks.get("DPDEMO-2").status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(tasks.get("DPDEMO-2").milestone()).isEqualTo("Beta");

        // Done wins over a leftover blocked label; Bug is a defect; Highest is Critical.
        ImportedTask crash = tasks.get("DPDEMO-3");
        assertThat(crash.status()).isEqualTo(TaskStatus.DONE);
        assertThat(crash.issueType()).isEqualTo(IssueType.DEFECT);
        assertThat(crash.priority()).isEqualTo(Priority.CRITICAL);
        assertThat(crash.milestone()).isNull();

        // A status literally named Blocked counts; unknown priority falls back to Medium with a warning.
        ImportedTask notes = tasks.get("DPDEMO-6");
        assertThat(notes.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(notes.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(notes.assignee()).isNull();
        assertThat(document.warnings()).extracting(ImportIssue::code).containsExactly("JIRA_UNKNOWN_PRIORITY");
    }

    @Test
    void blockedByLinksBecomeBlockerTextAndSameProjectDependencies() throws Exception {
        ImportDocument document = JiraIssueMapper.toDocument(JiraTestData.issues(), JiraTestData.properties("DPDEMO"));
        ImportProject project = document.projects().getFirst();

        ImportedTask loadTesting = project.tasks().stream()
                .filter(task -> task.taskKey().equals("DPDEMO-5")).findFirst().orElseThrow();
        assertThat(loadTesting.blockerText()).isEqualTo("DPDEMO-2, OTHER-9");
        // DPDEMO-2's own "blocks DPDEMO-5" (outward) link and the Relates link add nothing.
        assertThat(project.dependencies()).singleElement().satisfies(dependency -> {
            assertThat(dependency.dependentTaskKey()).isEqualTo("DPDEMO-5");
            assertThat(dependency.prerequisiteTaskKey()).isEqualTo("DPDEMO-2");
        });
    }

    @Test
    void warnsAboutConfiguredProjectsWithoutIssuesAndTheIssueLimit() throws Exception {
        JiraProperties limited = new JiraProperties("https://example.atlassian.net", "me@example.com", "token",
                java.util.List.of("DPDEMO", "EMPTY"), null, null, 2);

        ImportDocument document = JiraIssueMapper.toDocument(JiraTestData.issues(), limited);

        assertThat(document.projects().getFirst().tasks()).hasSize(2);
        assertThat(document.warnings()).extracting(ImportIssue::code)
                .containsExactly("JIRA_ISSUE_LIMIT", "JIRA_PROJECT_EMPTY");
    }
}
