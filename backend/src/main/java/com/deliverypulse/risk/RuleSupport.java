package com.deliverypulse.risk;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.deliverypulse.project.Project;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;

final class RuleSupport {

    private static final Pattern TASK_ID = Pattern.compile("(?i)^[a-z][a-z0-9_]*-\\d+$");

    private RuleSupport() {
    }

    static RiskFinding taskFinding(AnalysisContext context, RiskCategory category, RiskSeverity severity,
            RiskCertainty certainty, String identity, Task task, String title, String explanation,
            String evidence, String action) {
        return finding(context.project(), category, severity, certainty, identity,
                List.of(task), title, explanation, evidence, action);
    }

    static RiskFinding finding(AnalysisContext context, RiskCategory category, RiskSeverity severity,
            RiskCertainty certainty, String identity, Collection<Task> tasks, String title,
            String explanation, String evidence, String action) {
        return finding(context.project(), category, severity, certainty, identity,
                List.copyOf(tasks), title, explanation, evidence, action);
    }

    private static RiskFinding finding(Project project, RiskCategory category, RiskSeverity severity,
            RiskCertainty certainty, String identity, List<Task> tasks, String title,
            String explanation, String evidence, String action) {
        String fingerprint = project.getId() + "|" + category.name() + "|" + identity;
        return new RiskFinding(fingerprint, category, severity, certainty, title, explanation,
                evidence, action, tasks);
    }

    static boolean isOverdue(Task task, AnalysisContext context) {
        return task.getDueDate() != null
                && task.getDueDate().isBefore(context.analysisDate())
            && task.getStatus() != TaskStatus.DONE;
    }

    static boolean isTaskIdToken(String value) {
        return TASK_ID.matcher(value.trim()).matches();
    }

    static String normalizedTaskId(String taskId) {
        return taskId.trim().toUpperCase(Locale.ROOT);
    }

    static List<String> blockerTokens(Task task) {
        if (task.getBlockerText() == null || task.getBlockerText().isBlank()) {
            return List.of();
        }
        return task.getBlockerText().lines()
                .flatMap(line -> java.util.Arrays.stream(line.split(";")))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    static boolean hasDescriptiveBlocker(Task task, AnalysisContext context) {
        if (task.getStatus() == TaskStatus.DONE) {
            // Leftover blocker text on finished work is history, not a delivery risk.
            return false;
        }
        var knownTaskIds = context.tasks().stream()
                .map(candidate -> normalizedTaskId(candidate.getTaskKey()))
                .collect(java.util.stream.Collectors.toSet());
        return blockerTokens(task).stream().anyMatch(token ->
                !knownTaskIds.contains(normalizedTaskId(token)) && !isTaskIdToken(token));
    }

    static String taskReference(Task task) {
        return task.getTaskKey() + " (" + task.getTaskName() + ")";
    }
}