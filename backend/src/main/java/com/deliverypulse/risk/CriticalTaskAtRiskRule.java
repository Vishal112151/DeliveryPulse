package com.deliverypulse.risk;

import java.util.List;

import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;

@Component
public class CriticalTaskAtRiskRule implements RiskRule {

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        return context.tasks().stream()
                .filter(task -> task.getPriority() == Priority.CRITICAL)
                .filter(task -> RuleSupport.isOverdue(task, context)
                        || task.getStatus() == TaskStatus.BLOCKED
                        || RuleSupport.hasDescriptiveBlocker(task, context))
                .map(task -> {
                    boolean confirmed = RuleSupport.isOverdue(task, context)
                            || task.getStatus() == TaskStatus.BLOCKED;
                    String trigger = RuleSupport.isOverdue(task, context) ? "overdue"
                            : task.getStatus() == TaskStatus.BLOCKED ? "explicitly blocked"
                            : "has unresolved blocker text";
                    return RuleSupport.taskFinding(context, RiskCategory.CRITICAL_TASK_AT_RISK,
                            RiskSeverity.CRITICAL,
                            confirmed ? RiskCertainty.CONFIRMED : RiskCertainty.POTENTIAL,
                            task.getId().toString(), task,
                            "Critical-priority task at risk: " + task.getTaskKey(),
                            RuleSupport.taskReference(task) + " is Critical priority and " + trigger + ".",
                            "Task ID: " + task.getTaskKey() + "; priority: Critical; status: " + task.getStatus()
                                    + "; due date: " + task.getDueDate() + "; blocker text: " + task.getBlockerText(),
                            "Escalate to the project owner, confirm the recovery plan, and reassess dependent work.");
                })
                .toList();
    }
}