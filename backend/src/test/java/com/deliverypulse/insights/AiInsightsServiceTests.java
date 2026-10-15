package com.deliverypulse.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.deliverypulse.insights.ProjectInsightsResponse.Source;
import com.deliverypulse.insights.llm.LlmClient;
import com.deliverypulse.insights.llm.LlmException;
import com.deliverypulse.insights.llm.LlmFailure;
import com.deliverypulse.insights.llm.LlmPrompt;
import com.deliverypulse.insights.llm.LlmResult;
import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.task.TaskRepository;

class AiInsightsServiceTests {

    private static final UUID PROJECT_ID = InsightTestData.PROJECT.getId();
    private final LlmClient llmClient = mock(LlmClient.class);
    private final RiskAnalysisService riskAnalysisService = mock(RiskAnalysisService.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);

    @Test
    void returnsValidatedAiInsightsWhenEnabledConfiguredAndAuthorized() {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", true), true);
        when(llmClient.generate(any(LlmPrompt.class), eq(AiInsightDraft.class)))
                .thenReturn(new LlmResult<>(InsightTestData.validDraft(), "claude-opus-5-5", 1200, 400));

        ProjectInsightsResponse response = service.generateProjectInsights(PROJECT_ID);

        assertThat(response.source()).isEqualTo(Source.AI);
        assertThat(response.status()).isEqualTo(InsightStatus.GENERATED);
        assertThat(response.model()).isEqualTo("claude-opus-5-5");
        assertThat(response.riskExplanations().get(0).severity()).isEqualTo(RiskSeverity.CRITICAL);
        assertThat(response.disclaimer()).contains("deterministic");
        // Read-only: the service never asks the repository or risk engine to change anything.
        verify(taskRepository).findAllByProjectId(PROJECT_ID);
        verify(riskAnalysisService).getProjectRisks(PROJECT_ID);
        verifyNoMoreInteractions(taskRepository, riskAnalysisService);
    }

    @Test
    void neverCallsTheProviderWhenDisabled() {
        AiInsightsService service = service(InsightTestData.properties(false, "test-key", true), true);

        ProjectInsightsResponse response = service.generateProjectInsights(PROJECT_ID);

        assertThat(response.source()).isEqualTo(Source.DETERMINISTIC_FALLBACK);
        assertThat(response.status()).isEqualTo(InsightStatus.DISABLED);
        assertThat(response.executiveSummary()).isNotEmpty();
        assertThat(response.riskExplanations().get(0).riskId()).isEqualTo(InsightTestData.CRITICAL_RISK.id());
        verify(llmClient, never()).generate(any(), any());
    }

    @Test
    void neverSendsDataWithoutExplicitAuthorization() {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", false), true);

        ProjectInsightsResponse response = service.generateProjectInsights(PROJECT_ID);

        assertThat(response.status()).isEqualTo(InsightStatus.NOT_AUTHORIZED);
        verify(llmClient, never()).generate(any(), any());
    }

    @Test
    void reportsMissingCredentialsAsNotConfigured() {
        AiInsightsService service = service(InsightTestData.properties(true, "", true), false);

        assertThat(service.generateProjectInsights(PROJECT_ID).status()).isEqualTo(InsightStatus.NOT_CONFIGURED);
        assertThat(service.status().available()).isFalse();
        verify(llmClient, never()).generate(any(), any());
    }

    @Test
    void requiresAnAnalysisBeforeCallingTheProvider() {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", true), true);
        when(riskAnalysisService.getProjectRisks(PROJECT_ID)).thenReturn(InsightTestData.summary(null));

        ProjectInsightsResponse response = service.generateProjectInsights(PROJECT_ID);

        assertThat(response.status()).isEqualTo(InsightStatus.ANALYSIS_REQUIRED);
        verify(llmClient, never()).generate(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = LlmFailure.class)
    void fallsBackGracefullyForEveryProviderFailure(LlmFailure failure) {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", true), true);
        when(llmClient.generate(any(LlmPrompt.class), eq(AiInsightDraft.class)))
                .thenThrow(new LlmException(failure, "Provider problem."));

        ProjectInsightsResponse response = service.generateProjectInsights(PROJECT_ID);

        assertThat(response.source()).isEqualTo(Source.DETERMINISTIC_FALLBACK);
        assertThat(response.status()).isEqualTo(InsightStatus.valueOf(failure.name()));
        assertThat(response.message()).contains("Provider problem.", "deterministic summary");
        assertThat(response.executiveSummary()).isNotEmpty();
    }

    @Test
    void fallsBackWhenTheModelResponseFailsValidation() {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", true), true);
        AiInsightDraft hallucinated = new AiInsightDraft(List.of(new AiInsightDraft.DraftStatement(
                "Task ZZ-9 has collapsed.", StatementBasis.FACT, List.of("ZZ-9"))), null, null, null);
        when(llmClient.generate(any(LlmPrompt.class), eq(AiInsightDraft.class)))
                .thenReturn(new LlmResult<>(hallucinated, "claude-opus-5-5", 10, 10));

        ProjectInsightsResponse response = service.generateProjectInsights(PROJECT_ID);

        assertThat(response.status()).isEqualTo(InsightStatus.INVALID_RESPONSE);
        assertThat(response.source()).isEqualTo(Source.DETERMINISTIC_FALLBACK);
        assertThat(response.executiveSummary()).noneMatch(statement -> statement.text().contains("ZZ-9"));
    }

    @Test
    void deterministicFallbackReportsFactsWithTaskEvidence() {
        AiInsightsService service = service(InsightTestData.properties(false, "", false), false);

        ProjectInsightsResponse response = service.generateProjectInsights(PROJECT_ID);

        assertThat(response.executiveSummary()).anySatisfy(statement -> {
            assertThat(statement.text()).contains("past their due date", "AT-2");
            assertThat(statement.basis()).isEqualTo(StatementBasis.FACT);
            assertThat(statement.supportingTaskIds()).containsExactly("AT-2");
        });
        assertThat(response.weeklyStatus().progress()).isNotEmpty();
        assertThat(response.recommendedActions()).isNotEmpty();
    }

    private AiInsightsService service(AiInsightsProperties properties, boolean configured) {
        when(llmClient.isConfigured()).thenReturn(configured);
        when(llmClient.providerName()).thenReturn("anthropic");
        when(riskAnalysisService.getProjectRisks(PROJECT_ID))
                .thenReturn(InsightTestData.summary(InsightTestData.ANALYZED_AT));
        when(taskRepository.findAllByProjectId(PROJECT_ID)).thenReturn(InsightTestData.TASKS);
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
        return new AiInsightsService(properties, llmClient, riskAnalysisService, taskRepository,
                new InsightContextBuilder(properties), new InsightPromptFactory(InsightTestData.objectMapper()),
                new InsightResponseValidator(), new DeterministicInsightFallback(properties, clock), clock);
    }
}
