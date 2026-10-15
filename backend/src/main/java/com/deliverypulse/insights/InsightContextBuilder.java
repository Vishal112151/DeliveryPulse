package com.deliverypulse.insights;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.risk.RiskResponse;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;

/**
 * Selects the minimum project data the model needs: task records and the risks the deterministic
 * engine already detected. Nothing here queries the database or exposes anything beyond this project.
 */
@Component
public class InsightContextBuilder {

    static final Comparator<RiskResponse> PRIORITY_ORDER = Comparator
            .comparingInt((RiskResponse risk) -> severityRank(risk.severity()))
            .thenComparingInt(risk -> certaintyRank(risk.certainty()))
            .thenComparing(RiskResponse::title);

    private final AiInsightsProperties properties;

    public InsightContextBuilder(AiInsightsProperties properties) {
        this.properties = properties;
    }

    public PreparedInsightContext build(ProjectRiskSummary summary, List<Task> tasks) {
        List<RiskResponse> rankedRisks = summary.risks().stream().sorted(PRIORITY_ORDER).toList();
        List<RiskResponse> sentRisks = rankedRisks.stream().limit(properties.maxRisks()).toList();
        Map<String, RiskResponse> risksByRef = new LinkedHashMap<>();
        List<RiskRecord> riskRecords = new ArrayList<>();
        for (int index = 0; index < sentRisks.size(); index++) {
            RiskResponse risk = sentRisks.get(index);
            String ref = "R" + (index + 1);
            risksByRef.put(ref, risk);
            riskRecords.add(new RiskRecord(ref, risk.category().name(), risk.severity().name(),
                    risk.certainty().name(), limit(risk.title()), limit(risk.explanation()), limit(risk.evidence()),
                    risk.supportingTaskIds(), limit(risk.recommendedAction())));
        }
        List<String> explainRefs = risksByRef.keySet().stream().limit(properties.maxExplainedRisks()).toList();

        Set<String> riskTaskIds = new HashSet<>();
        sentRisks.forEach(risk -> risk.supportingTaskIds().forEach(id -> riskTaskIds.add(normalize(id))));
        List<Task> orderedTasks = tasks.stream()
                .sorted(Comparator
                        .comparing((Task task) -> !riskTaskIds.contains(normalize(task.getTaskKey())))
                        .thenComparing(task -> task.getStatus() == TaskStatus.DONE)
                        .thenComparing(Task::getTaskKey, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Task> sentTasks = orderedTasks.stream().limit(properties.maxTasks()).toList();

        Map<String, String> pseudonyms = new HashMap<>();
        Map<String, String> taskIdsByNormalized = new LinkedHashMap<>();
        tasks.forEach(task -> taskIdsByNormalized.put(normalize(task.getTaskKey()), task.getTaskKey()));
        LocalDate analysisDate = summary.analysisDate();
        List<TaskRecord> taskRecords = sentTasks.stream()
                .sorted(Comparator.comparing(Task::getTaskKey, String.CASE_INSENSITIVE_ORDER))
                .map(task -> new TaskRecord(
                        task.getTaskKey(),
                        limit(task.getTaskName()),
                        limit(task.getTeamOrModule()),
                        assignee(task.getAssignee(), pseudonyms),
                        task.getPriority().name(),
                        task.getStatus().name(),
                        task.getStartDate(),
                        task.getDueDate(),
                        task.getStatus() != TaskStatus.DONE && task.getDueDate() != null
                                && analysisDate != null && task.getDueDate().isBefore(analysisDate),
                        limit(task.getBlockerText()),
                        properties.includeTaskDescriptions() ? limit(task.getDescription()) : null))
                .toList();

        InsightPayload payload = new InsightPayload(
                summary.projectName(),
                analysisDate,
                new Metrics(summary.taskCount(), summary.completedTaskCount(), summary.incompleteTaskCount(),
                        summary.overdueTaskCount(), summary.blockedTaskCount(), summary.activeRiskCount()),
                taskRecords,
                riskRecords,
                explainRefs,
                tasks.size() - sentTasks.size(),
                rankedRisks.size() - sentRisks.size());
        return new PreparedInsightContext(payload, risksByRef, taskIdsByNormalized);
    }

    static String normalize(String taskId) {
        return taskId == null ? "" : taskId.trim().toUpperCase(Locale.ROOT);
    }

    private String assignee(String name, Map<String, String> pseudonyms) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (properties.includeAssigneeNames()) {
            return limit(name);
        }
        return pseudonyms.computeIfAbsent(name.trim().toLowerCase(Locale.ROOT),
                key -> "Person " + (pseudonyms.size() + 1));
    }

    private String limit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= properties.maxTextLength()
                ? trimmed : trimmed.substring(0, properties.maxTextLength()) + " [truncated]";
    }

    private static int severityRank(RiskSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    private static int certaintyRank(RiskCertainty certainty) {
        return switch (certainty) {
            case CONFIRMED -> 0;
            case POTENTIAL -> 1;
            case DATA_QUALITY -> 2;
        };
    }

    /** The data block serialized into the prompt. */
    public record InsightPayload(
            String projectName,
            LocalDate analysisDate,
            Metrics metrics,
            List<TaskRecord> tasks,
            List<RiskRecord> risks,
            List<String> explainRiskRefs,
            int omittedTaskCount,
            int omittedRiskCount) {
    }

    public record Metrics(int taskCount, int completedTaskCount, int incompleteTaskCount,
            int overdueTaskCount, int blockedTaskCount, int activeRiskCount) {
    }

    public record TaskRecord(String taskId, String name, String teamOrModule, String assignee,
            String priority, String status, LocalDate startDate, LocalDate dueDate, boolean overdue,
            String blockerText, String description) {
    }

    public record RiskRecord(String ref, String category, String severity, String certainty, String title,
            String explanation, String evidence, List<String> supportingTaskIds, String recommendedAction) {
    }

    /** Payload plus the lookups the validator needs to map model references back to real records. */
    public record PreparedInsightContext(
            InsightPayload payload,
            Map<String, RiskResponse> risksByRef,
            Map<String, String> taskIdsByNormalized) {
    }
}
