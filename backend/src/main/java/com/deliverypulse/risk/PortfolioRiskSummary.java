package com.deliverypulse.risk;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskSeverity;

public record PortfolioRiskSummary(
        LocalDate analysisDate,
        Instant analysisTimestamp,
        int projectCount,
        int taskCount,
        int completedTaskCount,
        int incompleteTaskCount,
        int overdueTaskCount,
        int blockedTaskCount,
        int activeRiskCount,
        Map<RiskSeverity, Long> risksBySeverity,
        Map<RiskCategory, Long> risksByCategory,
        List<ProjectRiskSummary> projects,
        int openDefectCount) {

    public PortfolioRiskSummary {
        risksBySeverity = Map.copyOf(risksBySeverity);
        risksByCategory = Map.copyOf(risksByCategory);
        projects = List.copyOf(projects);
    }
}