package com.deliverypulse.risk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.RiskStatus;

public record RiskResponse(
        UUID id,
        RiskCategory category,
        RiskSeverity severity,
        RiskCertainty certainty,
        RiskStatus status,
        String title,
        String explanation,
        String evidence,
        List<String> supportingTaskIds,
        String recommendedAction,
        Instant detectedAt) {

    public RiskResponse {
        supportingTaskIds = List.copyOf(supportingTaskIds);
    }
}