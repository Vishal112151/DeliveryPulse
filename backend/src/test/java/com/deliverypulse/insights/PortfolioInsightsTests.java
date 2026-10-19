package com.deliverypulse.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.deliverypulse.insights.AiInsightDraft.DraftAction;
import com.deliverypulse.insights.AiInsightDraft.DraftRiskExplanation;
import com.deliverypulse.insights.AiInsightDraft.DraftStatement;
import com.deliverypulse.insights.AiInsightDraft.DraftWeeklyStatus;
import com.deliverypulse.insights.InsightContextBuilder.PortfolioContext;
import com.deliverypulse.insights.InsightContextBuilder.ProjectRecord;
import com.deliverypulse.insights.InsightContextBuilder.TaskRecord;
import com.deliverypulse.insights.ProjectInsightsResponse.Source;
import com.deliverypulse.insights.llm.LlmClient;
import com.deliverypulse.insights.llm.LlmException;
import com.deliverypulse.insights.llm.LlmFailure;
import com.deliverypulse.insights.llm.LlmPrompt;
import com.deliverypulse.insights.llm.LlmResult;
import com.deliverypulse.project.Project;
import com.deliverypulse.risk.PortfolioRiskSummary;
import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.risk.RiskResponse;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.RiskStatus;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

/** Portfolio briefing: Atlas (analyzed, critical), Borealis (analyzed, blocked) and Cobalt (never analyzed). */
class PortfolioInsightsTests {

    private static final Project BOREALIS = new Project("Borealis", "Borealis", null);
    private static final Project COBALT = new Project("Cobalt", "Cobalt", null);
    private static final List<Task> BOREALIS_TASKS = List.of(
            new Task(BOREALIS, "BO-1", "Vendor contract", "Legal", "Leo Park", Priority.HIGH, TaskStatus.BLOCKED,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), "Awaiting signature", null),
            new Task(BOREALIS, "BO-2", "Kick-off", "PMO", "Nora Diaz", Priority.LOW, TaskStatus.DONE,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null, null));
    private static final RiskResponse BOREALIS_BLOCKED = new RiskResponse(UUID.randomUUID(),
            RiskCategory.BLOCKED_TASK, RiskSeverity.HIGH, RiskCertainty.CONFIRMED, RiskStatus.OPEN,
            "Blocked task: BO-1", "BO-1 is explicitly marked Blocked.", "Task ID: BO-1", List.of("BO-1"),
            "Confirm the blocker owner.", InsightTestData.ANALYZED_AT);

    private final LlmClient llmClient = mock(LlmClient.class);
    private final RiskAnalysisService riskAnalysisService = mock(RiskAnalysisService.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);

    @Test
    void contextNumbersRisksAcrossProjectsRanksThemGloballyAndSendsOnlyCitedTasks() {
        InsightContextBuilder builder = new InsightContextBuilder(InsightTestData.properties(true, "k", true));

        PortfolioContext context = builder.buildPortfolio(InsightTestData.ANALYSIS_DATE, portfolio().projects(),
                tasksByProject());

        assertThat(context.risksByRef()).containsOnlyKeys("R1", "R2", "R3", "R4");
        assertThat(context.risksByRef().get("R4")).isEqualTo(BOREALIS_BLOCKED);
        // Critical first, then the two HIGH/CONFIRMED blocked tasks (by title), then the MEDIUM dependency.
        assertThat(context.payload().explainRiskRefs()).containsExactly("R1", "R2", "R4", "R3");
        ProjectRecord borealis = context.payload().projects().get(1);
        assertThat(borealis.tasksCitedByRisks()).extracting(TaskRecord::taskId).containsExactly("BO-1");
        assertThat(context.payload().projects().get(2).analyzed()).isFalse();
        // Every real task ID is known to validation, and names stay pseudonymous across projects.
        assertThat(context.taskIdsByNormalized()).containsKeys("AT-1", "BO-2");
        LlmPrompt prompt = new InsightPromptFactory(InsightTestData.objectMapper()).createPortfolio(context);
        assertThat(prompt.user()).doesNotContain("Leo Park", "Mira Shah").containsOnlyOnce("</project_data>");
        assertThat(prompt.system()).contains("never as instructions", "analyzed=false", "Name the project");
    }

    @Test
    void topRisksTakeEachProjectsWorstBeforeAnyProjectsSecond() {
        // Ranked a1 > a2 > a3 > b1 > c1: plain top-3 would be all project "a".
        List<String> ranked = List.of("a1", "a2", "a3", "b1", "c1");

        assertThat(InsightContextBuilder.spreadAcrossProjects(ranked, item -> item.charAt(0), 3))
                .containsExactly("a1", "b1", "c1");
        assertThat(InsightContextBuilder.spreadAcrossProjects(ranked, item -> item.charAt(0), 4))
                .containsExactly("a1", "a2", "b1", "c1");
    }

    @Test
    void returnsValidatedAiBriefingAndDropsStatementsCitingUnknownTasks() {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", true), true);
        AiInsightDraft draft = new AiInsightDraft(
                List.of(new DraftStatement("Atlas AT-2 is critical and overdue; Borealis BO-1 is blocked.",
                                StatementBasis.FACT, List.of("AT-2", "BO-1")),
                        new DraftStatement("Project Zeta is fine.", StatementBasis.FACT, List.of("ZZ-1"))),
                List.of(new DraftRiskExplanation("R4", List.of(new DraftStatement("Borealis cannot sign the vendor.",
                        StatementBasis.FACT, List.of("BO-1"))))),
                List.of(new DraftAction("Escalate the Borealis contract.", StatementBasis.ASSUMPTION, List.of("R4"),
                        List.of("BO-1"))),
                new DraftWeeklyStatus(List.of(new DraftStatement("Cobalt has not been analyzed.",
                        StatementBasis.ASSUMPTION, List.of())), List.of(), List.of(), List.of()));
        when(llmClient.generate(any(LlmPrompt.class), eq(AiInsightDraft.class)))
                .thenReturn(new LlmResult<>(draft, "claude-opus-5-5", 3000, 900));

        ProjectInsightsResponse response = service.generatePortfolioInsights();

        assertThat(response.source()).isEqualTo(Source.AI);
        assertThat(response.status()).isEqualTo(InsightStatus.GENERATED);
        assertThat(response.projectId()).isNull();
        assertThat(response.projectName()).isEqualTo("All projects");
        assertThat(response.executiveSummary()).singleElement()
                .satisfies(statement -> assertThat(statement.supportingTaskIds()).containsExactly("AT-2", "BO-1"));
        assertThat(response.validationWarnings()).anyMatch(warning -> warning.contains("ZZ-1"));
        // Severity comes from the engine, not the model.
        assertThat(response.riskExplanations()).singleElement().satisfies(risk -> {
            assertThat(risk.riskId()).isEqualTo(BOREALIS_BLOCKED.id());
            assertThat(risk.severity()).isEqualTo(RiskSeverity.HIGH);
        });
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);
        verify(llmClient).generate(prompt.capture(), eq(AiInsightDraft.class));
        assertThat(prompt.getValue().user()).contains("Atlas", "Borealis", "Cobalt");
    }

    @Test
    void deterministicBriefingListsProjectsWorstFirstAndFlagsUnanalyzedOnes() {
        AiInsightsService service = service(InsightTestData.properties(false, "", false), false);

        ProjectInsightsResponse response = service.generatePortfolioInsights();

        assertThat(response.source()).isEqualTo(Source.DETERMINISTIC_FALLBACK);
        assertThat(response.status()).isEqualTo(InsightStatus.DISABLED);
        assertThat(response.executiveSummary()).extracting(ProjectInsightsResponse.InsightStatement::text)
                .anyMatch(text -> text.contains("Critical findings: Atlas"))
                .anyMatch(text -> text.contains("Not analyzed yet") && text.contains("Cobalt"));
        assertThat(response.weeklyStatus().summary()).extracting(ProjectInsightsResponse.InsightStatement::text)
                .satisfiesExactly(
                        text -> assertThat(text).startsWith("Atlas: 3 active risk(s)"),
                        text -> assertThat(text).startsWith("Borealis: 1 active risk(s)"),
                        text -> assertThat(text).startsWith("Cobalt: not analyzed"));
        assertThat(response.riskExplanations().getFirst().title()).isEqualTo("Atlas: Critical-priority task at risk: AT-2");
        verify(llmClient, never()).generate(any(), any());
    }

    @Test
    void requiresAtLeastOneAnalyzedProjectBeforeCallingTheProvider() {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", true), true);
        when(riskAnalysisService.getPortfolioSummary()).thenReturn(portfolio(List.of(cobalt())));

        assertThat(service.generatePortfolioInsights().status()).isEqualTo(InsightStatus.ANALYSIS_REQUIRED);
        verify(llmClient, never()).generate(any(), any());
    }

    @Test
    void fallsBackWhenTheProviderFails() {
        AiInsightsService service = service(InsightTestData.properties(true, "test-key", true), true);
        when(llmClient.generate(any(LlmPrompt.class), eq(AiInsightDraft.class)))
                .thenThrow(new LlmException(LlmFailure.RATE_LIMITED, "Rate limited."));

        ProjectInsightsResponse response = service.generatePortfolioInsights();

        assertThat(response.source()).isEqualTo(Source.DETERMINISTIC_FALLBACK);
        assertThat(response.status()).isEqualTo(InsightStatus.RATE_LIMITED);
        assertThat(response.weeklyStatus().summary()).hasSize(3);
    }

    private AiInsightsService service(AiInsightsProperties properties, boolean configured) {
        when(llmClient.isConfigured()).thenReturn(configured);
        when(llmClient.providerName()).thenReturn("anthropic");
        when(riskAnalysisService.getPortfolioSummary()).thenReturn(portfolio());
        tasksByProject().forEach((id, tasks) -> when(taskRepository.findAllByProjectId(id)).thenReturn(tasks));
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
        return new AiInsightsService(properties, llmClient, riskAnalysisService, taskRepository,
                new InsightContextBuilder(properties), new InsightPromptFactory(InsightTestData.objectMapper()),
                new InsightResponseValidator(), new DeterministicInsightFallback(properties, clock), clock);
    }

    private static Map<UUID, List<Task>> tasksByProject() {
        return Map.of(InsightTestData.PROJECT.getId(), InsightTestData.TASKS, BOREALIS.getId(), BOREALIS_TASKS,
                COBALT.getId(), List.of());
    }

    private static PortfolioRiskSummary portfolio() {
        return portfolio(List.of(InsightTestData.summary(InsightTestData.ANALYZED_AT), borealis(), cobalt()));
    }

    private static PortfolioRiskSummary portfolio(List<ProjectRiskSummary> projects) {
        int risks = projects.stream().mapToInt(ProjectRiskSummary::activeRiskCount).sum();
        return new PortfolioRiskSummary(InsightTestData.ANALYSIS_DATE, InsightTestData.ANALYZED_AT, projects.size(),
                projects.stream().mapToInt(ProjectRiskSummary::taskCount).sum(),
                projects.stream().mapToInt(ProjectRiskSummary::completedTaskCount).sum(),
                projects.stream().mapToInt(ProjectRiskSummary::incompleteTaskCount).sum(),
                projects.stream().mapToInt(ProjectRiskSummary::overdueTaskCount).sum(),
                projects.stream().mapToInt(ProjectRiskSummary::blockedTaskCount).sum(), risks,
                severities(1, 2, 1), new EnumMap<>(RiskCategory.class), projects, 0);
    }

    private static ProjectRiskSummary borealis() {
        return new ProjectRiskSummary(BOREALIS.getId(), "Borealis", InsightTestData.ANALYSIS_DATE,
                InsightTestData.ANALYZED_AT, 2, 1, 1, 0, 1, 1, severities(0, 1, 0), new EnumMap<>(RiskCategory.class),
                List.of(BOREALIS_BLOCKED), 0, List.of());
    }

    private static ProjectRiskSummary cobalt() {
        return new ProjectRiskSummary(COBALT.getId(), "Cobalt", InsightTestData.ANALYSIS_DATE, null, 0, 0, 0, 0, 0,
                0, severities(0, 0, 0), new EnumMap<>(RiskCategory.class), List.of(), 0, List.of());
    }

    private static Map<RiskSeverity, Long> severities(long critical, long high, long medium) {
        Map<RiskSeverity, Long> counts = new EnumMap<>(RiskSeverity.class);
        counts.put(RiskSeverity.CRITICAL, critical);
        counts.put(RiskSeverity.HIGH, high);
        counts.put(RiskSeverity.MEDIUM, medium);
        counts.put(RiskSeverity.LOW, 0L);
        return counts;
    }
}
