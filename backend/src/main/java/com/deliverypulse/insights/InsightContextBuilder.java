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
    /** Keeps a large portfolio's prompt bounded; the rest are counted in omittedRiskCount. */
    static final int PORTFOLIO_RISKS_PER_PROJECT = 5;

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
            riskRecords.add(riskRecord(ref, risk));
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
                .map(task -> taskRecord(task, analysisDate, pseudonyms))
                .toList();

        InsightPayload payload = new InsightPayload(
                summary.projectName(),
                analysisDate,
                metrics(summary),
                taskRecords,
                riskRecords,
                explainRefs,
                tasks.size() - sentTasks.size(),
                rankedRisks.size() - sentRisks.size());
        return new PreparedInsightContext(payload, risksByRef, taskIdsByNormalized);
    }

    /**
     * Portfolio view: every project's metrics, its top risks (refs unique across the portfolio) and only
     * the tasks those risks cite, so the payload stays small however many projects there are.
     */
    public PortfolioContext buildPortfolio(LocalDate snapshotDate, List<ProjectRiskSummary> summaries,
            Map<java.util.UUID, List<Task>> tasksByProject) {
        Map<String, RiskResponse> risksByRef = new LinkedHashMap<>();
        // ponytail: task IDs are matched portfolio-wide; two projects reusing one ID both count as "known".
        Map<String, String> taskIdsByNormalized = new LinkedHashMap<>();
        Map<String, String> pseudonyms = new HashMap<>();
        List<ProjectRecord> projects = new ArrayList<>();
        int omittedRisks = 0;
        for (ProjectRiskSummary summary : summaries) {
            List<Task> tasks = tasksByProject.getOrDefault(summary.projectId(), List.of());
            tasks.forEach(task -> taskIdsByNormalized.putIfAbsent(normalize(task.getTaskKey()), task.getTaskKey()));
            List<RiskResponse> ranked = summary.risks().stream().sorted(PRIORITY_ORDER).toList();
            List<RiskResponse> sent = ranked.stream().limit(PORTFOLIO_RISKS_PER_PROJECT).toList();
            omittedRisks += ranked.size() - sent.size();
            List<RiskRecord> riskRecords = new ArrayList<>();
            Set<String> citedTaskIds = new HashSet<>();
            for (RiskResponse risk : sent) {
                String ref = "R" + (risksByRef.size() + 1);
                risksByRef.put(ref, risk);
                riskRecords.add(riskRecord(ref, risk));
                risk.supportingTaskIds().forEach(id -> citedTaskIds.add(normalize(id)));
            }
            List<TaskRecord> citedTasks = tasks.stream()
                    .filter(task -> citedTaskIds.contains(normalize(task.getTaskKey())))
                    .sorted(Comparator.comparing(Task::getTaskKey, String.CASE_INSENSITIVE_ORDER))
                    .map(task -> taskRecord(task, summary.analysisDate(), pseudonyms))
                    .toList();
            projects.add(new ProjectRecord(limit(summary.projectName()), summary.analysisTimestamp() != null,
                    summary.analysisDate(), metrics(summary), riskRecords, citedTasks));
        }
        Map<RiskResponse, String> refByRisk = new java.util.IdentityHashMap<>();
        Map<RiskResponse, Object> projectByRisk = new java.util.IdentityHashMap<>();
        risksByRef.forEach((ref, risk) -> refByRisk.put(risk, ref));
        summaries.forEach(summary -> summary.risks().forEach(risk -> projectByRisk.put(risk, summary.projectId())));
        List<RiskResponse> ranked = risksByRef.values().stream().sorted(PRIORITY_ORDER).toList();
        List<String> explainRefs = spreadAcrossProjects(ranked, projectByRisk::get, properties.maxExplainedRisks())
                .stream().map(refByRisk::get).toList();
        return new PortfolioContext(new PortfolioPayload(snapshotDate, projects, explainRefs, omittedRisks),
                risksByRef, taskIdsByNormalized);
    }

    /**
     * Picks up to {@code limit} items from a priority-ranked list, taking each project's best item before any
     * project's second, so equally severe risks are not all drawn from one project.
     */
    static <T> List<T> spreadAcrossProjects(List<T> ranked, java.util.function.Function<T, Object> projectOf,
            int limit) {
        List<T> picked = new ArrayList<>();
        Set<Object> covered = new HashSet<>();
        ranked.stream().filter(item -> covered.add(projectOf.apply(item))).limit(limit).forEach(picked::add);
        ranked.stream().filter(item -> !picked.contains(item)).limit(Math.max(0, limit - picked.size()))
                .forEach(picked::add);
        return picked.stream().sorted(java.util.Comparator.comparingInt(ranked::indexOf)).toList();
    }

    private RiskRecord riskRecord(String ref, RiskResponse risk) {
        return new RiskRecord(ref, risk.category().name(), risk.severity().name(), risk.certainty().name(),
                limit(risk.title()), limit(risk.explanation()), limit(risk.evidence()), risk.supportingTaskIds(),
                limit(risk.recommendedAction()));
    }

    private TaskRecord taskRecord(Task task, LocalDate analysisDate, Map<String, String> pseudonyms) {
        return new TaskRecord(
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
                properties.includeTaskDescriptions() ? limit(task.getDescription()) : null,
                task.getIssueType().name(),
                limit(task.getMilestone()),
                task.getMilestoneDate());
    }

    private static Metrics metrics(ProjectRiskSummary summary) {
        return new Metrics(summary.taskCount(), summary.completedTaskCount(), summary.incompleteTaskCount(),
                summary.overdueTaskCount(), summary.blockedTaskCount(), summary.activeRiskCount());
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
            String blockerText, String description, String type, String milestone, LocalDate milestoneDate) {
    }

    public record RiskRecord(String ref, String category, String severity, String certainty, String title,
            String explanation, String evidence, List<String> supportingTaskIds, String recommendedAction) {
    }

    /** The portfolio data block serialized into the prompt. */
    public record PortfolioPayload(
            LocalDate snapshotDate,
            List<ProjectRecord> projects,
            List<String> explainRiskRefs,
            int omittedRiskCount) {
    }

    public record ProjectRecord(String name, boolean analyzed, LocalDate analysisDate, Metrics metrics,
            List<RiskRecord> risks, List<TaskRecord> tasksCitedByRisks) {
    }

    /** What the validator needs to map model references back to real records. */
    public interface InsightReferences {
        Map<String, RiskResponse> risksByRef();

        Map<String, String> taskIdsByNormalized();
    }

    /** Payload plus the lookups the validator needs to map model references back to real records. */
    public record PreparedInsightContext(
            InsightPayload payload,
            Map<String, RiskResponse> risksByRef,
            Map<String, String> taskIdsByNormalized) implements InsightReferences {
    }

    public record PortfolioContext(
            PortfolioPayload payload,
            Map<String, RiskResponse> risksByRef,
            Map<String, String> taskIdsByNormalized) implements InsightReferences {
    }
}
