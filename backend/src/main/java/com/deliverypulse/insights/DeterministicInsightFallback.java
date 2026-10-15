package com.deliverypulse.insights;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.deliverypulse.insights.ProjectInsightsResponse.ActionInsight;
import com.deliverypulse.insights.ProjectInsightsResponse.InsightStatement;
import com.deliverypulse.insights.ProjectInsightsResponse.RiskInsight;
import com.deliverypulse.insights.ProjectInsightsResponse.Source;
import com.deliverypulse.insights.ProjectInsightsResponse.WeeklyStatusInsight;
import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.risk.RiskResponse;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;

/**
 * Builds insights purely from stored tasks and deterministic risk findings. Used whenever AI is
 * disabled, unauthorized, unavailable, or returns an unusable response.
 */
@Component
public class DeterministicInsightFallback {

    static final String DISCLAIMER = "Generated from DeliveryPulse's deterministic rules and stored task records. "
            + "No AI was used.";

    private final AiInsightsProperties properties;
    private final Clock clock;

    public DeterministicInsightFallback(AiInsightsProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public ProjectInsightsResponse build(ProjectRiskSummary summary, List<Task> tasks,
            InsightStatus status, String message) {
        List<RiskResponse> ranked = summary.risks().stream().sorted(InsightContextBuilder.PRIORITY_ORDER).toList();
        List<RiskResponse> top = ranked.stream().limit(properties.maxExplainedRisks()).toList();

        List<String> overdue = tasks.stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE && task.getDueDate() != null
                        && summary.analysisDate() != null && task.getDueDate().isBefore(summary.analysisDate()))
                .map(Task::getTaskKey).sorted().toList();
        List<String> blocked = tasks.stream().filter(task -> task.getStatus() == TaskStatus.BLOCKED)
                .map(Task::getTaskKey).sorted().toList();
        List<String> done = tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE)
                .map(Task::getTaskKey).sorted().toList();
        List<String> inProgress = tasks.stream().filter(task -> task.getStatus() == TaskStatus.IN_PROGRESS)
                .map(Task::getTaskKey).sorted().toList();

        List<InsightStatement> executive = new ArrayList<>();
        executive.add(fact(summary.completedTaskCount() + " of " + summary.taskCount() + " tasks are Done"
                + (summary.analysisDate() == null ? "." : " as of " + summary.analysisDate() + "."), done));
        if (!overdue.isEmpty()) {
            executive.add(fact(overdue.size() + " incomplete task(s) are past their due date: "
                    + String.join(", ", overdue) + ".", overdue));
        }
        if (!blocked.isEmpty()) {
            executive.add(fact(blocked.size() + " task(s) are marked Blocked: " + String.join(", ", blocked) + ".",
                    blocked));
        }
        executive.add(fact(summary.activeRiskCount() + " active risk finding(s): " + severityBreakdown(summary)
                + ".", List.of()));

        List<RiskInsight> riskInsights = top.stream()
                .map(risk -> new RiskInsight(risk.id(), risk.title(), risk.severity(), risk.category(),
                        risk.certainty(), risk.supportingTaskIds(),
                        List.of(fact(risk.explanation(), risk.supportingTaskIds()))))
                .toList();

        Map<String, List<RiskResponse>> risksByAction = new LinkedHashMap<>();
        top.stream().filter(risk -> risk.recommendedAction() != null)
                .forEach(risk -> risksByAction.computeIfAbsent(risk.recommendedAction(), key -> new ArrayList<>())
                        .add(risk));
        List<ActionInsight> actions = risksByAction.entrySet().stream()
                .map(entry -> new ActionInsight(entry.getKey(), StatementBasis.FACT,
                        entry.getValue().stream().flatMap(risk -> risk.supportingTaskIds().stream())
                                .distinct().sorted().toList(),
                        entry.getValue().stream().map(RiskResponse::id).toList()))
                .toList();

        List<InsightStatement> progress = new ArrayList<>();
        if (!done.isEmpty()) {
            progress.add(fact("Completed: " + String.join(", ", done) + ".", done));
        }
        if (!inProgress.isEmpty()) {
            progress.add(fact("In progress: " + String.join(", ", inProgress) + ".", inProgress));
        }
        List<InsightStatement> risksAndIssues = top.stream()
                .map(risk -> fact(risk.severity() + ": " + risk.title(), risk.supportingTaskIds()))
                .toList();
        List<InsightStatement> nextSteps = actions.stream()
                .map(action -> fact(action.action(), action.supportingTaskIds()))
                .toList();

        return new ProjectInsightsResponse(summary.projectId(), summary.projectName(),
                Source.DETERMINISTIC_FALLBACK, status, message, null, clock.instant(), summary.analysisDate(),
                List.copyOf(executive), riskInsights, actions,
                new WeeklyStatusInsight(List.copyOf(executive), List.copyOf(progress), risksAndIssues, nextSteps),
                List.of(), DISCLAIMER);
    }

    private static String severityBreakdown(ProjectRiskSummary summary) {
        List<String> parts = new ArrayList<>();
        for (RiskSeverity severity : List.of(RiskSeverity.CRITICAL, RiskSeverity.HIGH, RiskSeverity.MEDIUM,
                RiskSeverity.LOW)) {
            long count = summary.risksBySeverity().getOrDefault(severity, 0L);
            if (count > 0) {
                parts.add(count + " " + severity.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private static InsightStatement fact(String text, List<String> taskIds) {
        return new InsightStatement(text, StatementBasis.FACT, List.copyOf(taskIds));
    }
}
