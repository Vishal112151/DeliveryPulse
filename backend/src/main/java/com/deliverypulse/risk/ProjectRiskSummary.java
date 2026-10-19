package com.deliverypulse.risk;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskSeverity;

public record ProjectRiskSummary(
        UUID projectId,
        String projectName,
        LocalDate analysisDate,
        Instant analysisTimestamp,
        int taskCount,
        int completedTaskCount,
        int incompleteTaskCount,
        int overdueTaskCount,
        int blockedTaskCount,
        int activeRiskCount,
        Map<RiskSeverity, Long> risksBySeverity,
        Map<RiskCategory, Long> risksByCategory,
        List<RiskResponse> risks,
        int openDefectCount,
        List<MilestoneSummary> milestones) {

    public ProjectRiskSummary {
        risksBySeverity = Map.copyOf(risksBySeverity);
        risksByCategory = Map.copyOf(risksByCategory);
        risks = List.copyOf(risks);
        milestones = List.copyOf(milestones);
    }
}