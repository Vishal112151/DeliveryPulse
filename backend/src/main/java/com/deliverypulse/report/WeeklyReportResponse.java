package com.deliverypulse.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskSeverity;

public record WeeklyReportResponse(
        LocalDate weekStartDate,
        LocalDate weekEndDate,
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
        List<ProjectRiskSummary> projects) {

    public WeeklyReportResponse {
        risksBySeverity = Map.copyOf(risksBySeverity);
        risksByCategory = Map.copyOf(risksByCategory);
        projects = List.copyOf(projects);
    }
}