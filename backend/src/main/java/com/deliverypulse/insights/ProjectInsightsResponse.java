package com.deliverypulse.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;

/**
 * Insights returned to the frontend. Severity, category and certainty always come from the
 * deterministic risk engine; AI text only explains them.
 */
public record ProjectInsightsResponse(
        UUID projectId,
        String projectName,
        Source source,
        InsightStatus status,
        String message,
        String model,
        Instant generatedAt,
        LocalDate analysisDate,
        List<InsightStatement> executiveSummary,
        List<RiskInsight> riskExplanations,
        List<ActionInsight> recommendedActions,
        WeeklyStatusInsight weeklyStatus,
        List<String> validationWarnings,
        String disclaimer) {

    public enum Source {
        AI,
        DETERMINISTIC_FALLBACK
    }

    public record InsightStatement(String text, StatementBasis basis, List<String> supportingTaskIds) {
    }

    public record RiskInsight(
            UUID riskId,
            String title,
            RiskSeverity severity,
            RiskCategory category,
            RiskCertainty certainty,
            List<String> supportingTaskIds,
            List<InsightStatement> explanation) {
    }

    public record ActionInsight(
            String action,
            StatementBasis basis,
            List<String> supportingTaskIds,
            List<UUID> relatedRiskIds) {
    }

    public record WeeklyStatusInsight(
            List<InsightStatement> summary,
            List<InsightStatement> progress,
            List<InsightStatement> risksAndIssues,
            List<InsightStatement> nextSteps) {
    }
}
