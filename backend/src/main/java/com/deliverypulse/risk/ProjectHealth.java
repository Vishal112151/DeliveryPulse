package com.deliverypulse.risk;

import java.util.Map;

import com.deliverypulse.shared.domain.RiskSeverity;

/**
 * One-glance project status from its open findings: any Critical is Off track, any High is At risk, otherwise
 * On track. The frontend applies the same rule (shared/project-health.ts); keep the two in step.
 */
public enum ProjectHealth {
    OFF_TRACK("Off track"),
    AT_RISK("At risk"),
    ON_TRACK("On track"),
    NOT_ANALYZED("Not analyzed");

    private final String label;

    ProjectHealth(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ProjectHealth of(Map<RiskSeverity, Long> risksBySeverity, boolean analyzed) {
        if (!analyzed) {
            return NOT_ANALYZED;
        }
        if (risksBySeverity.getOrDefault(RiskSeverity.CRITICAL, 0L) > 0) {
            return OFF_TRACK;
        }
        return risksBySeverity.getOrDefault(RiskSeverity.HIGH, 0L) > 0 ? AT_RISK : ON_TRACK;
    }
}
