package com.deliverypulse.risk;

import java.util.List;

import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;

@Component
public class BlockedTaskRule implements RiskRule {

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        return context.tasks().stream()
                .filter(task -> task.getStatus() == TaskStatus.BLOCKED
                        || RuleSupport.hasDescriptiveBlocker(task, context))
                .map(task -> {
                    boolean explicitlyBlocked = task.getStatus() == TaskStatus.BLOCKED;
                    RiskCertainty certainty = explicitlyBlocked
                            ? RiskCertainty.CONFIRMED : RiskCertainty.POTENTIAL;
                    String evidence = "Task ID: " + task.getTaskKey() + "; status: " + task.getStatus()
                            + "; blocker text: " + task.getBlockerText();
                    return RuleSupport.taskFinding(context, RiskCategory.BLOCKED_TASK,
                            RiskSeverity.HIGH, certainty, task.getId().toString(), task,
                            "Blocked task: " + task.getTaskKey(),
                            explicitlyBlocked
                                    ? RuleSupport.taskReference(task) + " is explicitly marked Blocked."
                                    : RuleSupport.taskReference(task) + " has descriptive blocker text, but the source does not record a resolution state.",
                            evidence,
                            "Confirm the blocker, record an owner, and update the task when it is resolved.");
                })
                .toList();
    }
}