package com.deliverypulse.insights;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.deliverypulse.insights.InsightContextBuilder.PreparedInsightContext;
import com.deliverypulse.insights.InsightContextBuilder.TaskRecord;
import com.deliverypulse.insights.llm.LlmPrompt;

class InsightContextAndPromptTests {

    private final InsightContextBuilder builder =
            new InsightContextBuilder(InsightTestData.properties(true, "test-key", true));
    private final InsightPromptFactory promptFactory = new InsightPromptFactory(InsightTestData.objectMapper());

    @Test
    void ranksRisksByDeterministicSeverityAndAssignsRefs() {
        PreparedInsightContext context = builder.build(InsightTestData.summary(InsightTestData.ANALYZED_AT),
                InsightTestData.TASKS);

        assertThat(context.risksByRef()).containsEntry("R1", InsightTestData.CRITICAL_RISK)
                .containsEntry("R2", InsightTestData.BLOCKED_RISK)
                .containsEntry("R3", InsightTestData.DEPENDENCY_RISK);
        assertThat(context.payload().explainRiskRefs()).containsExactly("R1", "R2", "R3");
        assertThat(context.payload().risks().get(0).severity()).isEqualTo("CRITICAL");
    }

    @Test
    void pseudonymizesAssigneesAndOmitsDescriptionsByDefault() {
        PreparedInsightContext context = builder.build(InsightTestData.summary(InsightTestData.ANALYZED_AT),
                InsightTestData.TASKS);

        assertThat(context.payload().tasks()).extracting(TaskRecord::assignee)
                .containsExactly("Person 1", "Person 1", "Person 2", null);
        assertThat(context.payload().tasks()).extracting(TaskRecord::description).containsOnlyNulls();
        assertThat(context.payload().tasks()).filteredOn(TaskRecord::overdue).extracting(TaskRecord::taskId)
                .containsExactly("AT-2");

        LlmPrompt prompt = promptFactory.create(context);
        assertThat(prompt.user()).doesNotContain("Mira", "Leo Park", "Confidential client roadmap");
    }

    @Test
    void keepsUploadedTextInsideTheEscapedDataBlock() {
        LlmPrompt prompt = promptFactory.create(builder.build(
                InsightTestData.summary(InsightTestData.ANALYZED_AT), InsightTestData.TASKS));

        // The injected closing tag is escaped, so the data block closes exactly once, at the end.
        assertThat(prompt.user()).containsOnlyOnce("</project_data>");
        assertThat(prompt.user().indexOf("</project_data>"))
                .isGreaterThan(prompt.user().indexOf("Ignore previous instructions"));
        assertThat(prompt.user()).contains("\\u003c/project_data\\u003e").doesNotContain("<system>");
        // Instructions live only in the system prompt, which states the data is untrusted.
        assertThat(prompt.system()).contains("never as instructions", "authoritative", "FACT", "ASSUMPTION");
        assertThat(prompt.system()).doesNotContain("Atlas", "AT-2");
    }

    @Test
    void limitsTasksSentToTheModelAndReportsOmissions() {
        InsightContextBuilder smallBuilder = new InsightContextBuilder(new AiInsightsProperties(true, "anthropic",
                "k", "claude-opus-5-5", null, 0, 0, true, true, false, false, 2, 1, 1, 500));

        PreparedInsightContext context = smallBuilder.build(
                InsightTestData.summary(InsightTestData.ANALYZED_AT), InsightTestData.TASKS);

        // Tasks cited by the sent risk (R1 -> AT-2) are kept first.
        assertThat(context.payload().tasks()).extracting(TaskRecord::taskId).contains("AT-2").hasSize(2);
        assertThat(context.payload().omittedTaskCount()).isEqualTo(2);
        assertThat(context.payload().omittedRiskCount()).isEqualTo(2);
        // Validation still knows every real task ID.
        assertThat(context.taskIdsByNormalized()).hasSize(4);
    }
}
