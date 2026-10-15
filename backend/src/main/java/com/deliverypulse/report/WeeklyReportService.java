package com.deliverypulse.report;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.deliverypulse.risk.PortfolioRiskSummary;
import com.deliverypulse.risk.RiskAnalysisService;

@Service
public class WeeklyReportService {

    private final RiskAnalysisService riskAnalysisService;

    public WeeklyReportService(RiskAnalysisService riskAnalysisService) {
        this.riskAnalysisService = riskAnalysisService;
    }

    public WeeklyReportResponse getWeeklyReport() {
        PortfolioRiskSummary portfolio = riskAnalysisService.getPortfolioSummary();
        LocalDate endDate = portfolio.analysisDate();
        return new WeeklyReportResponse(endDate.minusDays(6), endDate, portfolio.analysisDate(),
                portfolio.analysisTimestamp(), portfolio.projectCount(), portfolio.taskCount(),
                portfolio.completedTaskCount(), portfolio.incompleteTaskCount(), portfolio.overdueTaskCount(),
                portfolio.blockedTaskCount(), portfolio.activeRiskCount(), portfolio.risksBySeverity(),
                portfolio.risksByCategory(), portfolio.projects());
    }
}