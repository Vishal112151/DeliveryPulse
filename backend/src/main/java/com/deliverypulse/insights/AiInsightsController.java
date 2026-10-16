package com.deliverypulse.insights;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "AI insights", description = "Optional AI explanations layered over the deterministic risk analysis.")
public class AiInsightsController {

    private final AiInsightsService aiInsightsService;

    public AiInsightsController(AiInsightsService aiInsightsService) {
        this.aiInsightsService = aiInsightsService;
    }

    @GetMapping("/api/ai/status")
    @Operation(summary = "Report whether AI insights are enabled, configured and authorized")
    public AiStatusResponse getStatus() {
        return aiInsightsService.status();
    }

    @PostMapping("/api/projects/{projectId}/insights")
    @Operation(summary = "Generate project insights",
            description = "Returns AI-generated summaries, risk explanations, actions and a weekly status draft "
                    + "when AI is enabled and authorized; otherwise (or on any provider failure) returns the "
                    + "deterministic fallback with a status explaining why. Never modifies stored data.")
    public ProjectInsightsResponse generateInsights(@PathVariable UUID projectId) {
        return aiInsightsService.generateProjectInsights(projectId);
    }

    @PostMapping("/api/portfolio/insights")
    @Operation(summary = "Generate a portfolio briefing across all projects",
            description = "Same shape as project insights, with projectId null and projectName \"All projects\". "
                    + "AI-generated when enabled and authorized, otherwise the deterministic fallback. "
                    + "Never modifies stored data.")
    public ProjectInsightsResponse generatePortfolioInsights() {
        return aiInsightsService.generatePortfolioInsights();
    }
}
