package com.deliverypulse.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.project.Project;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;

class RiskRulesTests {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 10, 15);
    private final Project project = new Project("Risk Rules", "Risk Rules", null);

    @Test
    void overdueRuleUsesStrictBeforeBoundaryAndExcludesDoneTasks() {
        Task overdue = task("RISK-1", Priority.HIGH, TaskStatus.IN_PROGRESS, ANALYSIS_DATE.minusDays(1), "Owner", null);
        Task dueToday = task("RISK-2", Priority.HIGH, TaskStatus.NOT_STARTED, ANALYSIS_DATE, "Owner", null);
        Task doneOverdue = task("RISK-3", Priority.HIGH, TaskStatus.DONE, ANALYSIS_DATE.minusDays(10), "Owner", null);

        List<RiskFinding> findings = new OverdueTaskRule().evaluate(context(List.of(overdue, dueToday, doneOverdue)));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.category()).isEqualTo(RiskCategory.OVERDUE_TASK);
            assertThat(finding.severity()).isEqualTo(RiskSeverity.HIGH);
            assertThat(finding.certainty()).isEqualTo(RiskCertainty.CONFIRMED);
            assertThat(finding.supportingTasks()).containsExactly(overdue);
        });
    }

    @Test
    void blockedRuleDistinguishesExplicitStatusFromDescriptiveText() {
        Task explicitlyBlocked = task("RISK-10", Priority.MEDIUM, TaskStatus.BLOCKED,
                ANALYSIS_DATE.plusDays(2), "Owner", null);
        Task descriptiveBlocker = task("RISK-11", Priority.MEDIUM, TaskStatus.IN_PROGRESS,
                ANALYSIS_DATE.plusDays(2), "Owner", " Waiting for vendor approval ");
        Task unresolvedIdOnly = task("RISK-12", Priority.MEDIUM, TaskStatus.IN_PROGRESS,
                ANALYSIS_DATE.plusDays(2), "Owner", "OTHER-404");

        List<RiskFinding> findings = new BlockedTaskRule().evaluate(
                context(List.of(explicitlyBlocked, descriptiveBlocker, unresolvedIdOnly)));

        assertThat(findings).extracting(RiskFinding::certainty)
                .containsExactly(RiskCertainty.CONFIRMED, RiskCertainty.POTENTIAL);
        assertThat(findings).extracting(RiskFinding::severity)
                .containsOnly(RiskSeverity.HIGH);
    }

    @Test
    void criticalAtRiskRuleEscalatesOverdueAndExplicitlyBlockedCriticalTasks() {
        Task overdueCritical = task("RISK-20", Priority.CRITICAL, TaskStatus.IN_PROGRESS,
                ANALYSIS_DATE.minusDays(1), "Owner", null);
        Task blockedCritical = task("RISK-21", Priority.CRITICAL, TaskStatus.BLOCKED,
                ANALYSIS_DATE.plusDays(1), "Owner", null);
        Task doneCritical = task("RISK-22", Priority.CRITICAL, TaskStatus.DONE,
                ANALYSIS_DATE.minusDays(1), "Owner", null);

        List<RiskFinding> findings = new CriticalTaskAtRiskRule().evaluate(
                context(List.of(overdueCritical, blockedCritical, doneCritical)));

        assertThat(findings).hasSize(2).allSatisfy(finding -> {
            assertThat(finding.category()).isEqualTo(RiskCategory.CRITICAL_TASK_AT_RISK);
            assertThat(finding.severity()).isEqualTo(RiskSeverity.CRITICAL);
            assertThat(finding.certainty()).isEqualTo(RiskCertainty.CONFIRMED);
        });
    }

    @Test
    void dependencyRuleUsesSameProjectAndEscalatesOnlyWhenPrerequisiteIsOverdueOrBlocked() {
        Task onTimePrerequisite = task("RISK-30", Priority.MEDIUM, TaskStatus.IN_PROGRESS,
                ANALYSIS_DATE, "Owner", null);
        Task overduePrerequisite = task("RISK-31", Priority.MEDIUM, TaskStatus.IN_PROGRESS,
                ANALYSIS_DATE.minusDays(1), "Owner", null);
        Task dependentOne = task("RISK-32", Priority.MEDIUM, TaskStatus.NOT_STARTED,
                ANALYSIS_DATE.plusDays(3), "Owner", null);
        Task dependentTwo = task("RISK-33", Priority.MEDIUM, TaskStatus.NOT_STARTED,
                ANALYSIS_DATE.plusDays(3), "Owner", null);
        Project anotherProject = new Project("Other Project", "Other Project", null);
        Task crossProjectTask = new Task(anotherProject, "OTHER-1", "External task", Priority.MEDIUM, TaskStatus.IN_PROGRESS);

        List<RiskFinding> findings = new DependencyRiskRule().evaluate(context(
                List.of(onTimePrerequisite, overduePrerequisite, dependentOne, dependentTwo),
                List.of(new Dependency(dependentOne, onTimePrerequisite, "RISK-30"),
                        new Dependency(onTimePrerequisite, overduePrerequisite, "RISK-31"),
                        new Dependency(dependentTwo, overduePrerequisite, "RISK-31"),
                        new Dependency(dependentOne, crossProjectTask, "OTHER-1"))));

        assertThat(findings).hasSize(3);
        assertThat(findings).extracting(RiskFinding::severity)
                .containsExactly(RiskSeverity.MEDIUM, RiskSeverity.HIGH, RiskSeverity.HIGH);
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.certainty()).isEqualTo(RiskCertainty.POTENTIAL);
            assertThat(finding.supportingTasks()).hasSize(2);
        });
    }

    @Test
    void dataQualityRuleFindsMissingFieldsAndOnlyTaskIdShapedUnknownReferences() {
        Task incomplete = task("RISK-40", Priority.MEDIUM, TaskStatus.IN_PROGRESS,
                null, null, "UNKNOWN-77; waiting for a vendor");
        Task knownReference = task("RISK-41", Priority.MEDIUM, TaskStatus.NOT_STARTED,
                ANALYSIS_DATE.plusDays(1), "Owner", "RISK-40");

        List<RiskFinding> findings = new DataQualityRule().evaluate(context(List.of(incomplete, knownReference)));

        assertThat(findings).hasSize(3);
        assertThat(findings).extracting(RiskFinding::category)
                .containsOnly(RiskCategory.DATA_QUALITY_WARNING);
        assertThat(findings).extracting(RiskFinding::certainty)
                .containsOnly(RiskCertainty.DATA_QUALITY);
        assertThat(findings).extracting(RiskFinding::severity)
                .containsOnly(RiskSeverity.LOW);
        assertThat(findings).filteredOn(finding -> finding.title().startsWith("Unresolved dependency"))
                .singleElement()
                .extracting(RiskFinding::evidence)
                .asString()
                .contains("UNKNOWN-77");
        assertThat(findings).filteredOn(finding -> finding.title().startsWith("Task reference is not normalized"))
                .singleElement()
                .extracting(RiskFinding::evidence)
                .asString()
                .contains("RISK-40");
    }

    @Test
    void dependencyDataQualityWarnsAboutCrossProjectReferencesAndStaleOpenEdges() {
        Task dependent = task("RISK-50", Priority.MEDIUM, TaskStatus.IN_PROGRESS,
                ANALYSIS_DATE.plusDays(2), "Owner", null);
        Task external = new Task(new Project("External", "External", null),
                "EXT-1", "External prerequisite", Priority.MEDIUM, TaskStatus.IN_PROGRESS);
        Task completedPrerequisite = task("RISK-51", Priority.MEDIUM, TaskStatus.DONE,
                ANALYSIS_DATE, "Owner", null);
        List<Dependency> dependencies = List.of(
                new Dependency(dependent, external, "EXT-1"),
                new Dependency(dependent, completedPrerequisite, "RISK-51"));

        List<RiskFinding> findings = new DataQualityRule().evaluate(context(List.of(dependent, completedPrerequisite), dependencies));

        assertThat(findings).hasSize(2).allSatisfy(finding -> {
            assertThat(finding.category()).isEqualTo(RiskCategory.DATA_QUALITY_WARNING);
            assertThat(finding.severity()).isEqualTo(RiskSeverity.LOW);
        });
    }

    @Test
    void completedTasksWithLeftoverBlockerTextAreNotReportedAsBlockedOrAtRisk() {
        Task done = task("RISK-60", Priority.CRITICAL, TaskStatus.DONE,
                ANALYSIS_DATE.plusDays(2), "Owner", "Waiting for vendor approval");

        assertThat(new BlockedTaskRule().evaluate(context(List.of(done)))).isEmpty();
        assertThat(new CriticalTaskAtRiskRule().evaluate(context(List.of(done)))).isEmpty();
    }

    private AnalysisContext context(List<Task> tasks) {
        return context(tasks, List.of());
    }

    private AnalysisContext context(List<Task> tasks, List<Dependency> dependencies) {
        return new AnalysisContext(project, ANALYSIS_DATE, tasks, dependencies);
    }

    private Task task(String id, Priority priority, TaskStatus status, LocalDate dueDate,
            String assignee, String blockerText) {
        return new Task(project, id, "Task " + id, "Core", assignee, priority, status,
                dueDate == null ? null : dueDate.minusDays(3), dueDate, blockerText, null);
    }
}