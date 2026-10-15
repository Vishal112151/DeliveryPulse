package com.deliverypulse.risk;

import java.util.List;

import org.springframework.stereotype.Component;

import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.shared.domain.DependencyStatus;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;

@Component
public class DependencyRiskRule implements RiskRule {

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        return context.dependencies().stream()
                .filter(dependency -> dependency.getStatus() == DependencyStatus.OPEN)
                .filter(dependency -> dependency.getDependentTask().getProject().getId().equals(context.project().getId()))
                .filter(dependency -> dependency.getPrerequisiteTask().getProject().getId().equals(context.project().getId()))
                .filter(dependency -> dependency.getDependentTask().getStatus() != TaskStatus.DONE)
                .filter(dependency -> dependency.getPrerequisiteTask().getStatus() != TaskStatus.DONE)
                .map(dependency -> finding(context, dependency))
                .toList();
    }

    private RiskFinding finding(AnalysisContext context, Dependency dependency) {
        var dependent = dependency.getDependentTask();
        var prerequisite = dependency.getPrerequisiteTask();
        boolean prerequisiteOverdue = RuleSupport.isOverdue(prerequisite, context);
        boolean prerequisiteBlocked = prerequisite.getStatus() == TaskStatus.BLOCKED;
        RiskSeverity severity = prerequisiteOverdue || prerequisiteBlocked
                ? RiskSeverity.HIGH : RiskSeverity.MEDIUM;
        String identity = dependent.getId() + "->" + prerequisite.getId();
        return RuleSupport.finding(context, RiskCategory.DEPENDENCY_RISK, severity, RiskCertainty.POTENTIAL,
                identity, List.of(dependent, prerequisite),
                "Unresolved dependency: " + dependent.getTaskKey() + " waits on " + prerequisite.getTaskKey(),
                "The open dependency links " + RuleSupport.taskReference(dependent) + " to incomplete prerequisite "
                        + RuleSupport.taskReference(prerequisite) + ". This may prevent the dependent task from progressing; it does not establish a missed project deadline.",
                "Dependent task ID: " + dependent.getTaskKey() + "; prerequisite task ID: "
                        + prerequisite.getTaskKey() + "; prerequisite status: " + prerequisite.getStatus()
                        + "; dependency status: " + dependency.getStatus(),
                "Confirm the prerequisite owner and expected completion date; update or resolve the dependency when verified.");
    }
}