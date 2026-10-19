package com.deliverypulse.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.project.Project;
import com.deliverypulse.shared.domain.IssueType;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskStatusChange;

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

    @Test
    void commaSeparatedTaskIdsAreDependenciesNotDescriptiveBlockers() {
        Task first = task("RISK-70", Priority.MEDIUM, TaskStatus.IN_PROGRESS, ANALYSIS_DATE.plusDays(2), "Owner", null);
        Task second = task("RISK-71", Priority.MEDIUM, TaskStatus.IN_PROGRESS, ANALYSIS_DATE.plusDays(2), "Owner", null);
        Task waiting = task("RISK-72", Priority.MEDIUM, TaskStatus.NOT_STARTED,
                ANALYSIS_DATE.plusDays(5), "Owner", "RISK-70, RISK-71");

        assertThat(new BlockedTaskRule().evaluate(context(List.of(first, second, waiting)))).isEmpty();
    }

    @Test
    void milestoneRuleConfirmsMissedMilestonesAndFlagsAtRiskOnesByProximity() {
        LocalDate past = ANALYSIS_DATE.minusDays(1);
        LocalDate soon = ANALYSIS_DATE.plusDays(10);
        LocalDate later = ANALYSIS_DATE.plusDays(60);
        Task missedOpen = milestoneTask("MS-1", TaskStatus.IN_PROGRESS, past.minusDays(2), "Alpha", past);
        Task missedDone = milestoneTask("MS-2", TaskStatus.DONE, past.minusDays(2), "Alpha", past);
        Task blockedSoon = milestoneTask("MS-3", TaskStatus.BLOCKED, soon, "Beta", soon);
        Task lateForLater = milestoneTask("MS-4", TaskStatus.NOT_STARTED, later.plusDays(5), "GA", later);
        Task onTrack = milestoneTask("MS-5", TaskStatus.IN_PROGRESS, later.minusDays(5), "GA", later);
        Task allDone = milestoneTask("MS-6", TaskStatus.DONE, past, "Closed", past);

        List<RiskFinding> findings = new MilestoneRiskRule().evaluate(
                context(List.of(missedOpen, missedDone, blockedSoon, lateForLater, onTrack, allDone)));

        assertThat(findings).extracting(RiskFinding::title)
                .containsExactly("Milestone missed: Alpha", "Milestone at risk: Beta", "Milestone at risk: GA");
        assertThat(findings).extracting(RiskFinding::severity)
                .containsExactly(RiskSeverity.CRITICAL, RiskSeverity.HIGH, RiskSeverity.MEDIUM);
        assertThat(findings).extracting(RiskFinding::certainty)
                .containsExactly(RiskCertainty.CONFIRMED, RiskCertainty.POTENTIAL, RiskCertainty.POTENTIAL);
        assertThat(findings.get(0).supportingTasks()).containsExactly(missedOpen);
        assertThat(findings.get(2).supportingTasks()).containsExactly(lateForLater);
        assertThat(findings).allSatisfy(finding -> assertThat(finding.category()).isEqualTo(RiskCategory.DELIVERY_RISK));
    }

    @Test
    void defectRuleFlagsOnlyOpenHighAndCriticalDefects() {
        Task criticalBug = defect("BUG-1", Priority.CRITICAL, TaskStatus.IN_PROGRESS);
        Task highBug = defect("BUG-2", Priority.HIGH, TaskStatus.NOT_STARTED);
        Task mediumBug = defect("BUG-3", Priority.MEDIUM, TaskStatus.NOT_STARTED);
        Task fixedBug = defect("BUG-4", Priority.CRITICAL, TaskStatus.DONE);
        Task criticalTask = task("BUG-5", Priority.CRITICAL, TaskStatus.IN_PROGRESS, ANALYSIS_DATE.plusDays(3), "Owner", null);

        List<RiskFinding> findings = new DefectRiskRule().evaluate(
                context(List.of(criticalBug, highBug, mediumBug, fixedBug, criticalTask)));

        assertThat(findings).extracting(finding -> finding.supportingTasks().getFirst())
                .containsExactly(criticalBug, highBug);
        assertThat(findings).extracting(RiskFinding::severity).containsExactly(RiskSeverity.CRITICAL, RiskSeverity.HIGH);
        assertThat(findings).allSatisfy(finding -> assertThat(finding.category()).isEqualTo(RiskCategory.DEFECT_RISK));
    }

    @Test
    void recurringBlockerRuleCountsBlockedEntriesFromHistoryAndIgnoresDoneTasks() {
        Task twice = task("REC-1", Priority.MEDIUM, TaskStatus.IN_PROGRESS, ANALYSIS_DATE.plusDays(5), "Owner", null);
        Task thrice = task("REC-2", Priority.MEDIUM, TaskStatus.BLOCKED, ANALYSIS_DATE.plusDays(5), "Owner", null);
        Task once = task("REC-3", Priority.MEDIUM, TaskStatus.BLOCKED, ANALYSIS_DATE.plusDays(5), "Owner", null);
        Task doneNow = task("REC-4", Priority.MEDIUM, TaskStatus.DONE, ANALYSIS_DATE.plusDays(5), "Owner", null);
        List<TaskStatusChange> history = new java.util.ArrayList<>();
        blockedTimes(history, twice, 2);
        blockedTimes(history, thrice, 3);
        blockedTimes(history, once, 1);
        blockedTimes(history, doneNow, 2);

        List<RiskFinding> findings = new RecurringBlockerRule().evaluate(
                new AnalysisContext(project, ANALYSIS_DATE, List.of(twice, thrice, once, doneNow), List.of(), history));

        assertThat(findings).extracting(finding -> finding.supportingTasks().getFirst()).containsExactly(twice, thrice);
        assertThat(findings).extracting(RiskFinding::severity).containsExactly(RiskSeverity.HIGH, RiskSeverity.CRITICAL);
        assertThat(findings.getFirst().evidence()).contains("2026-10-01, 2026-10-02");
    }

    private void blockedTimes(List<TaskStatusChange> history, Task task, int times) {
        history.add(new TaskStatusChange(task, null, TaskStatus.NOT_STARTED, Instant.parse("2026-09-30T09:00:00Z")));
        for (int day = 1; day <= times; day++) {
            history.add(new TaskStatusChange(task, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED,
                    Instant.parse("2026-10-0" + day + "T09:00:00Z")));
        }
    }

    private Task milestoneTask(String id, TaskStatus status, LocalDate dueDate, String milestone, LocalDate milestoneDate) {
        return new Task(project, id, "Task " + id, "Core", "Owner", Priority.MEDIUM, status, null, dueDate, null, null,
                IssueType.TASK, milestone, milestoneDate);
    }

    private Task defect(String id, Priority priority, TaskStatus status) {
        return new Task(project, id, "Defect " + id, "Core", "Owner", priority, status, null,
                ANALYSIS_DATE.plusDays(3), null, null, IssueType.DEFECT, null, null);
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