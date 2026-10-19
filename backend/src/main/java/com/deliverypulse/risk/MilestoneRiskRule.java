package com.deliverypulse.risk;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;

/** Flags milestones that were missed, or whose open work is blocked, overdue or scheduled after the milestone. */
@Component
public class MilestoneRiskRule implements RiskRule {

    static final int NEAR_MILESTONE_DAYS = 14;

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        Map<String, List<Task>> byMilestone = new LinkedHashMap<>();
        context.tasks().stream()
                .filter(task -> task.getMilestone() != null && task.getMilestoneDate() != null)
                .forEach(task -> byMilestone.computeIfAbsent(task.getMilestone().toLowerCase(Locale.ROOT),
                        key -> new ArrayList<>()).add(task));

        List<RiskFinding> findings = new ArrayList<>();
        byMilestone.forEach((identity, tasks) -> {
            String name = tasks.get(0).getMilestone();
            LocalDate milestoneDate = tasks.get(0).getMilestoneDate();
            List<Task> open = tasks.stream().filter(task -> task.getStatus() != TaskStatus.DONE).toList();
            if (open.isEmpty()) {
                return;
            }
            if (milestoneDate.isBefore(context.analysisDate())) {
                findings.add(RuleSupport.finding(context, RiskCategory.DELIVERY_RISK, RiskSeverity.CRITICAL,
                        RiskCertainty.CONFIRMED, identity, open,
                        "Milestone missed: " + name,
                        "Milestone '" + name + "' was due on " + milestoneDate + " and " + open.size() + " of "
                                + tasks.size() + " tasks are still open.",
                        evidence(name, milestoneDate, open),
                        "Agree a new milestone date with stakeholders and re-plan the open tasks against it."));
                return;
            }
            List<Task> blocked = open.stream().filter(task -> task.getStatus() == TaskStatus.BLOCKED).toList();
            List<Task> overdue = open.stream().filter(task -> RuleSupport.isOverdue(task, context)).toList();
            List<Task> late = open.stream()
                    .filter(task -> task.getDueDate() != null && task.getDueDate().isAfter(milestoneDate)).toList();
            List<Task> atRisk = open.stream()
                    .filter(task -> blocked.contains(task) || overdue.contains(task) || late.contains(task)).toList();
            if (atRisk.isEmpty()) {
                return;
            }
            long daysLeft = ChronoUnit.DAYS.between(context.analysisDate(), milestoneDate);
            List<String> reasons = new ArrayList<>();
            if (!blocked.isEmpty()) {
                reasons.add(blocked.size() + " blocked");
            }
            if (!overdue.isEmpty()) {
                reasons.add(overdue.size() + " overdue");
            }
            if (!late.isEmpty()) {
                reasons.add(late.size() + " due after the milestone date");
            }
            findings.add(RuleSupport.finding(context, RiskCategory.DELIVERY_RISK,
                    daysLeft <= NEAR_MILESTONE_DAYS ? RiskSeverity.HIGH : RiskSeverity.MEDIUM,
                    RiskCertainty.POTENTIAL, identity, atRisk,
                    "Milestone at risk: " + name,
                    "Milestone '" + name + "' is due on " + milestoneDate + " (" + daysLeft + " days). Open tasks: "
                            + String.join(", ", reasons) + ".",
                    evidence(name, milestoneDate, atRisk),
                    "Review the listed tasks with their owners, then either recover them before the milestone "
                            + "or move them out of it."));
        });
        return findings;
    }

    private static String evidence(String name, LocalDate milestoneDate, List<Task> tasks) {
        StringBuilder evidence = new StringBuilder("Milestone: " + name + "; date: " + milestoneDate + "; tasks: ");
        for (int index = 0; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            evidence.append(index == 0 ? "" : ", ").append(task.getTaskKey()).append(" (")
                    .append(task.getStatus()).append(", due ").append(task.getDueDate()).append(')');
        }
        return evidence.toString();
    }
}
