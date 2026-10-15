package com.deliverypulse.insights;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.deliverypulse.insights.InsightContextBuilder.PreparedInsightContext;
import com.deliverypulse.insights.InsightResponseValidator.InvalidInsightResponseException;
import com.deliverypulse.insights.InsightResponseValidator.ValidatedInsights;
import com.deliverypulse.insights.ProjectInsightsResponse.Source;
import com.deliverypulse.insights.llm.LlmClient;
import com.deliverypulse.insights.llm.LlmException;
import com.deliverypulse.insights.llm.LlmResult;
import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

/**
 * Optional AI layer over the deterministic engine. It only reads tasks and already-detected risks,
 * never writes, and always returns a usable response (AI output or the deterministic fallback).
 * Deliberately not transactional: the provider call must not hold a database connection.
 */
@Service
public class AiInsightsService {

    static final String AI_DISCLAIMER = "AI-generated draft. Severity, certainty and task data come from "
            + "DeliveryPulse's deterministic rules; verify any ASSUMPTION before acting on it.";
    private static final Logger logger = LoggerFactory.getLogger(AiInsightsService.class);

    private final AiInsightsProperties properties;
    private final LlmClient llmClient;
    private final RiskAnalysisService riskAnalysisService;
    private final TaskRepository taskRepository;
    private final InsightContextBuilder contextBuilder;
    private final InsightPromptFactory promptFactory;
    private final InsightResponseValidator validator;
    private final DeterministicInsightFallback fallback;
    private final Clock clock;

    public AiInsightsService(AiInsightsProperties properties, LlmClient llmClient,
            RiskAnalysisService riskAnalysisService, TaskRepository taskRepository,
            InsightContextBuilder contextBuilder, InsightPromptFactory promptFactory,
            InsightResponseValidator validator, DeterministicInsightFallback fallback, Clock clock) {
        this.properties = properties;
        this.llmClient = llmClient;
        this.riskAnalysisService = riskAnalysisService;
        this.taskRepository = taskRepository;
        this.contextBuilder = contextBuilder;
        this.promptFactory = promptFactory;
        this.validator = validator;
        this.fallback = fallback;
        this.clock = clock;
    }

    public AiStatusResponse status() {
        InsightStatus status = availability();
        return new AiStatusResponse(properties.enabled(), llmClient.isConfigured(),
                properties.externalDataSharingAuthorized(), status == InsightStatus.GENERATED,
                llmClient.providerName(), properties.model(), status, availabilityMessage(status));
    }

    public ProjectInsightsResponse generateProjectInsights(UUID projectId) {
        ProjectRiskSummary summary = riskAnalysisService.getProjectRisks(projectId);
        List<Task> tasks = taskRepository.findAllByProjectId(projectId);
        if (summary.analysisTimestamp() == null) {
            return fallback.build(summary, tasks, InsightStatus.ANALYSIS_REQUIRED,
                    "Run risk analysis for this project first; insights explain the analysis findings.");
        }
        InsightStatus availability = availability();
        if (availability != InsightStatus.GENERATED) {
            return fallback.build(summary, tasks, availability, availabilityMessage(availability));
        }

        PreparedInsightContext context = contextBuilder.build(summary, tasks);
        long started = System.nanoTime();
        try {
            LlmResult<AiInsightDraft> result = llmClient.generate(promptFactory.create(context), AiInsightDraft.class);
            ValidatedInsights insights = validator.validate(result.content(), context,
                    properties.maxExplainedRisks());
            logger.info("AI insights generated: project={}, model={}, inputTokens={}, outputTokens={}, "
                    + "warnings={}, elapsedMs={}", projectId, result.model(), result.inputTokens(),
                    result.outputTokens(), insights.warnings().size(), elapsedMs(started));
            return new ProjectInsightsResponse(summary.projectId(), summary.projectName(), Source.AI,
                    InsightStatus.GENERATED, "AI insights generated from the latest analysis.", result.model(),
                    clock.instant(), summary.analysisDate(), insights.executiveSummary(),
                    insights.riskExplanations(), insights.recommendedActions(), insights.weeklyStatus(),
                    insights.warnings(), AI_DISCLAIMER);
        } catch (LlmException exception) {
            InsightStatus status = InsightStatus.valueOf(exception.getFailure().name());
            // Log the category only: prompts and responses may contain project data.
            logger.warn("AI insights unavailable, using deterministic fallback: project={}, reason={}, elapsedMs={}",
                    projectId, status, elapsedMs(started));
            return fallback.build(summary, tasks, status, exception.getMessage()
                    + " Showing the deterministic summary instead.");
        } catch (InvalidInsightResponseException exception) {
            logger.warn("AI insights rejected by validation, using deterministic fallback: project={}, reason={}",
                    projectId, exception.getMessage());
            return fallback.build(summary, tasks, InsightStatus.INVALID_RESPONSE,
                    "The AI response failed validation. Showing the deterministic summary instead.");
        }
    }

    private InsightStatus availability() {
        if (!properties.enabled()) {
            return InsightStatus.DISABLED;
        }
        if (!llmClient.isConfigured()) {
            return InsightStatus.NOT_CONFIGURED;
        }
        if (!properties.externalDataSharingAuthorized()) {
            return InsightStatus.NOT_AUTHORIZED;
        }
        return InsightStatus.GENERATED;
    }

    private static String availabilityMessage(InsightStatus status) {
        return switch (status) {
            case GENERATED -> "AI insights are available.";
            case DISABLED -> "AI insights are turned off. Showing the deterministic summary.";
            case NOT_CONFIGURED -> "AI insights are enabled but no provider API key is configured. "
                    + "Showing the deterministic summary.";
            case NOT_AUTHORIZED -> "Sending project data to the external AI provider has not been authorized. "
                    + "Showing the deterministic summary.";
            default -> "AI insights are unavailable. Showing the deterministic summary.";
        };
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
