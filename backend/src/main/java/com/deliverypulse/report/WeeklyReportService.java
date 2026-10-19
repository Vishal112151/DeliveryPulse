package com.deliverypulse.report;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.risk.PortfolioRiskSummary;
import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.task.TaskStatusChange;
import com.deliverypulse.task.TaskStatusChangeRepository;

@Service
public class WeeklyReportService {

    private final RiskAnalysisService riskAnalysisService;
    private final TaskStatusChangeRepository statusChangeRepository;
    private final Clock clock;

    public WeeklyReportService(RiskAnalysisService riskAnalysisService,
            TaskStatusChangeRepository statusChangeRepository, Clock clock) {
        this.riskAnalysisService = riskAnalysisService;
        this.statusChangeRepository = statusChangeRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WeeklyReportResponse getWeeklyReport() {
        PortfolioRiskSummary portfolio = riskAnalysisService.getPortfolioSummary();
        LocalDate endDate = portfolio.analysisDate();
        LocalDate startDate = endDate.minusDays(6);
        List<TaskStatusChange> changes = statusChangeRepository.findAllChangedBetween(
                startDate.atStartOfDay(clock.getZone()).toInstant(),
                endDate.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
        Map<UUID, List<TaskStatusChange>> changesByProject = changes.stream()
                .collect(Collectors.groupingBy(change -> change.getTask().getProject().getId()));
        Map<UUID, WeeklyActivity> activityByProject = portfolio.projects().stream()
                .collect(Collectors.toMap(ProjectRiskSummary::projectId,
                        project -> WeeklyActivity.of(changesByProject.getOrDefault(project.projectId(), List.of()))));
        return new WeeklyReportResponse(startDate, endDate, portfolio.analysisDate(),
                portfolio.analysisTimestamp(), portfolio.projectCount(), portfolio.taskCount(),
                portfolio.completedTaskCount(), portfolio.incompleteTaskCount(), portfolio.overdueTaskCount(),
                portfolio.blockedTaskCount(), portfolio.activeRiskCount(), portfolio.risksBySeverity(),
                portfolio.risksByCategory(), portfolio.projects(), portfolio.openDefectCount(),
                WeeklyActivity.of(changes), activityByProject);
    }
}
