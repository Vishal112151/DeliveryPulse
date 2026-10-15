package com.deliverypulse.insights;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The JSON shape the model must return (structured outputs derive the schema from these records).
 * Drafts are untrusted until {@link InsightResponseValidator} has checked them against project data.
 */
public record AiInsightDraft(
        @JsonPropertyDescription("2-5 statements on overall delivery health for an executive reader")
        List<DraftStatement> executiveSummary,
        @JsonPropertyDescription("One entry per risk ref listed in explainRiskRefs, highest priority first")
        List<DraftRiskExplanation> riskExplanations,
        @JsonPropertyDescription("3-6 concrete next actions tied to the risks and tasks that justify them")
        List<DraftAction> recommendedActions,
        @JsonPropertyDescription("Draft weekly project status report")
        DraftWeeklyStatus weeklyStatus) {

    public record DraftStatement(
            @JsonPropertyDescription("One or two plain-language sentences")
            String text,
            @JsonPropertyDescription("FACT if stated directly by the records (must cite task IDs); otherwise ASSUMPTION")
            StatementBasis basis,
            @JsonPropertyDescription("Task IDs from the data that support this statement, exactly as written")
            List<String> supportingTaskIds) {
    }

    public record DraftRiskExplanation(
            @JsonPropertyDescription("Risk ref from the data, for example R1")
            String riskRef,
            @JsonPropertyDescription("Why the risk matters and what it could affect")
            List<DraftStatement> explanation) {
    }

    public record DraftAction(
            @JsonPropertyDescription("The recommended action, one sentence")
            String action,
            StatementBasis basis,
            @JsonPropertyDescription("Risk refs this action addresses")
            List<String> relatedRiskRefs,
            List<String> supportingTaskIds) {
    }

    public record DraftWeeklyStatus(
            List<DraftStatement> summary,
            List<DraftStatement> progress,
            List<DraftStatement> risksAndIssues,
            List<DraftStatement> nextSteps) {
    }
}
