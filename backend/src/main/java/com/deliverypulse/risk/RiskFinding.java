package com.deliverypulse.risk;

import java.util.List;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.task.Task;

public record RiskFinding(
        String fingerprint,
        RiskCategory category,
        RiskSeverity severity,
        RiskCertainty certainty,
        String title,
        String explanation,
        String evidence,
        String recommendedAction,
        List<Task> supportingTasks) {

    public RiskFinding {
        supportingTasks = List.copyOf(supportingTasks);
    }
}