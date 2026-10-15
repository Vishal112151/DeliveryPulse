package com.deliverypulse.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.deliverypulse.insights.AiInsightDraft.DraftAction;
import com.deliverypulse.insights.AiInsightDraft.DraftRiskExplanation;
import com.deliverypulse.insights.AiInsightDraft.DraftStatement;
import com.deliverypulse.insights.InsightContextBuilder.PreparedInsightContext;
import com.deliverypulse.insights.InsightResponseValidator.InvalidInsightResponseException;
import com.deliverypulse.insights.InsightResponseValidator.ValidatedInsights;
import com.deliverypulse.insights.ProjectInsightsResponse.InsightStatement;
import com.deliverypulse.shared.domain.RiskSeverity;

class InsightResponseValidatorTests {

    private final InsightResponseValidator validator = new InsightResponseValidator();
    private final PreparedInsightContext context = new InsightContextBuilder(
            InsightTestData.properties(true, "k", true))
            .build(InsightTestData.summary(InsightTestData.ANALYZED_AT), InsightTestData.TASKS);

    @Test
    void acceptsAValidDraftAndAttachesDeterministicRiskMetadata() {
        ValidatedInsights result = validator.validate(InsightTestData.validDraft(), context, 5);

        assertThat(result.warnings()).isEmpty();
        // Case-insensitive citation "at-2" is normalized to the stored spelling.
        assertThat(result.executiveSummary().get(0).supportingTaskIds()).containsExactly("AT-2");
        assertThat(result.riskExplanations()).hasSize(2);
        assertThat(result.riskExplanations().get(0).riskId()).isEqualTo(InsightTestData.CRITICAL_RISK.id());
        assertThat(result.riskExplanations().get(0).severity()).isEqualTo(RiskSeverity.CRITICAL);
        assertThat(result.recommendedActions().get(0).relatedRiskIds())
                .containsExactly(InsightTestData.CRITICAL_RISK.id());
        assertThat(result.weeklyStatus().nextSteps()).hasSize(1);
    }

    @Test
    void removesStatementsCitingTasksThatDoNotExist() {
        AiInsightDraft draft = withSummary(
                new DraftStatement("AT-2 is overdue.", StatementBasis.FACT, List.of("AT-2")),
                new DraftStatement("AT-99 is late.", StatementBasis.FACT, List.of("AT-99")));

        ValidatedInsights result = validator.validate(draft, context, 5);

        assertThat(result.executiveSummary()).extracting(InsightStatement::text).containsExactly("AT-2 is overdue.");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("unknown task ID 'AT-99'"));
    }

    @Test
    void relabelsFactsWithoutEvidenceAsAssumptions() {
        AiInsightDraft draft = withSummary(
                new DraftStatement("The team is understaffed.", StatementBasis.FACT, List.of()),
                new DraftStatement("Unlabelled point.", null, null));

        ValidatedInsights result = validator.validate(draft, context, 5);

        assertThat(result.executiveSummary()).extracting(InsightStatement::basis)
                .containsOnly(StatementBasis.ASSUMPTION);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("relabelled ASSUMPTION"));
    }

    @Test
    void dropsUnknownRiskRefsAndActionsThatCiteThem() {
        AiInsightDraft base = InsightTestData.validDraft();
        AiInsightDraft draft = new AiInsightDraft(base.executiveSummary(),
                List.of(new DraftRiskExplanation("R42", List.of(new DraftStatement("Invented risk.",
                        StatementBasis.ASSUMPTION, List.of())))),
                List.of(new DraftAction("Escalate the invented risk.", StatementBasis.ASSUMPTION,
                        List.of("R42"), List.of())),
                base.weeklyStatus());

        ValidatedInsights result = validator.validate(draft, context, 5);

        assertThat(result.riskExplanations()).isEmpty();
        assertThat(result.recommendedActions()).isEmpty();
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void capsExplainedRisksAndStripsControlCharacters() {
        AiInsightDraft base = InsightTestData.validDraft();
        AiInsightDraft draft = new AiInsightDraft(
                List.of(new DraftStatement("Line one\u0007\nline two", StatementBasis.FACT, List.of("AT-1"))),
                base.riskExplanations(), base.recommendedActions(), base.weeklyStatus());

        ValidatedInsights result = validator.validate(draft, context, 1);

        assertThat(result.executiveSummary().get(0).text()).isEqualTo("Line one\nline two");
        assertThat(result.riskExplanations()).hasSize(1);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("beyond the top 1"));
    }

    @Test
    void rejectsDraftsWithoutAUsableExecutiveSummary() {
        assertThatThrownBy(() -> validator.validate(null, context, 5))
                .isInstanceOf(InvalidInsightResponseException.class);
        AiInsightDraft onlyHallucinated = withSummary(
                new DraftStatement("ZZ-1 failed.", StatementBasis.FACT, List.of("ZZ-1")));
        assertThatThrownBy(() -> validator.validate(onlyHallucinated, context, 5))
                .isInstanceOf(InvalidInsightResponseException.class);
        AiInsightDraft overlong = withSummary(new DraftStatement("x".repeat(5000), StatementBasis.ASSUMPTION,
                List.of()));
        assertThatThrownBy(() -> validator.validate(overlong, context, 5))
                .isInstanceOf(InvalidInsightResponseException.class);
    }

    private static AiInsightDraft withSummary(DraftStatement... statements) {
        AiInsightDraft base = InsightTestData.validDraft();
        return new AiInsightDraft(List.of(statements), base.riskExplanations(), base.recommendedActions(),
                base.weeklyStatus());
    }
}
