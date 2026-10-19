package com.deliverypulse.risk;

import java.util.List;

import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;

/** Each open High or Critical priority defect is a delivery risk of the same severity. */
@Component
public class DefectRiskRule implements RiskRule {

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        return context.tasks().stream()
                .filter(task -> task.isDefect() && task.getStatus() != TaskStatus.DONE)
                .filter(task -> task.getPriority() == Priority.HIGH || task.getPriority() == Priority.CRITICAL)
                .map(task -> {
                    boolean critical = task.getPriority() == Priority.CRITICAL;
                    return RuleSupport.taskFinding(context, RiskCategory.DEFECT_RISK,
                            critical ? RiskSeverity.CRITICAL : RiskSeverity.HIGH, RiskCertainty.CONFIRMED,
                            task.getId().toString(), task,
                            "Open " + (critical ? "critical" : "high-priority") + " defect: " + task.getTaskKey(),
                            RuleSupport.taskReference(task) + " is an open " + task.getPriority() + " priority defect"
                                    + (task.getMilestone() == null ? "." : " in milestone '" + task.getMilestone() + "'."),
                            "Task ID: " + task.getTaskKey() + "; type: Defect; priority: " + task.getPriority()
                                    + "; status: " + task.getStatus() + "; due date: " + task.getDueDate(),
                            "Confirm an owner and fix date for the defect, and decide whether it blocks the release.");
                })
                .toList();
    }
}
