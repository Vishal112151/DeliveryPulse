package com.deliverypulse.risk;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Risk analysis", description = "Deterministic, evidence-backed project and portfolio risk analysis.")
public class RiskAnalysisController {

    private final RiskAnalysisService riskAnalysisService;

    public RiskAnalysisController(RiskAnalysisService riskAnalysisService) {
        this.riskAnalysisService = riskAnalysisService;
    }

    @PostMapping("/api/projects/{projectId}/analyze")
    @Operation(summary = "Analyze project delivery risks",
            description = "Reruns deterministic rules and refreshes current findings. An optional ISO date makes analysis reproducible.")
    public ProjectRiskSummary analyzeProject(
            @PathVariable UUID projectId,
            @Parameter(description = "Analysis date in yyyy-MM-dd format; defaults to today in APP_TIME_ZONE")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate analysisDate) {
        return riskAnalysisService.analyzeProject(projectId, analysisDate);
    }

    @GetMapping("/api/projects/{projectId}/risks")
    @Operation(summary = "Get active project risk findings")
    public ProjectRiskSummary getProjectRisks(@PathVariable UUID projectId) {
        return riskAnalysisService.getProjectRisks(projectId);
    }

    @GetMapping("/api/dashboard/portfolio")
    @Operation(summary = "Get portfolio risk aggregation")
    public PortfolioRiskSummary getPortfolioSummary() {
        return riskAnalysisService.getPortfolioSummary();
    }
}