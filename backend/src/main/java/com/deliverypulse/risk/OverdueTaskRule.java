package com.deliverypulse.risk;

import java.util.List;

import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;

@Component
public class OverdueTaskRule implements RiskRule {

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        return context.tasks().stream()
                .filter(task -> RuleSupport.isOverdue(task, context))
                .map(task -> RuleSupport.taskFinding(context, RiskCategory.OVERDUE_TASK,
                        RiskSeverity.HIGH, RiskCertainty.CONFIRMED, task.getId().toString(), task,
                        "Overdue task: " + task.getTaskKey(),
                        RuleSupport.taskReference(task) + " was due " + task.getDueDate()
                                + ", before the analysis date " + context.analysisDate()
                                + ", and is not done.",
                        "Task ID: " + task.getTaskKey() + "; due date: " + task.getDueDate()
                                + "; status: " + task.getStatus(),
                        "Confirm the current delivery date, assign an owner if needed, and agree on recovery steps."))
                .toList();
    }
}