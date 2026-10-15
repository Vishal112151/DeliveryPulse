package com.deliverypulse.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.deliverypulse.shared.domain.RiskSeverity;

public record ProjectResponse(
        UUID id,
        String name,
        String sourceSheetName,
        String description,
        Instant createdAt,
        Instant updatedAt,
        LocalDate analysisDate,
        Instant analysisTimestamp,
        int taskCount,
        int completedTaskCount,
        int incompleteTaskCount,
        int overdueTaskCount,
        int blockedTaskCount,
        Map<RiskSeverity, Long> risksBySeverity) {

    public ProjectResponse {
        risksBySeverity = Map.copyOf(risksBySeverity);
    }
}