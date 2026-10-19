package com.deliverypulse.report;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.deliverypulse.risk.PortfolioRiskSummary;
import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.task.TaskStatusChange;
import com.deliverypulse.task.TaskStatusChangeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WeeklyReportService {

    private final RiskAnalysisService riskAnalysisService;
    private final TaskStatusChangeRepository statusChangeRepository;
    private final WeeklyReportSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WeeklyReportService(RiskAnalysisService riskAnalysisService,
            TaskStatusChangeRepository statusChangeRepository, WeeklyReportSnapshotRepository snapshotRepository,
            ObjectMapper objectMapper, Clock clock) {
        this.riskAnalysisService = riskAnalysisService;
        this.statusChangeRepository = statusChangeRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Saves the current weekly report exactly as the report endpoint would return it now. */
    @Transactional
    public SnapshotSummary saveSnapshot(WeeklyReportSnapshot.Trigger trigger) {
        WeeklyReportResponse report = getWeeklyReport();
        try {
            WeeklyReportSnapshot snapshot = snapshotRepository.save(new WeeklyReportSnapshot(report.weekStartDate(),
                    report.weekEndDate(), clock.instant(), trigger, objectMapper.writeValueAsString(report)));
            return SnapshotSummary.of(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Weekly report could not be serialized", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<SnapshotSummary> listSnapshots() {
        return snapshotRepository.findAllByOrderByGeneratedAtDesc().stream().map(SnapshotSummary::of).toList();
    }

    /** The stored JSON, unchanged: the same shape as GET /api/reports/weekly. */
    @Transactional(readOnly = true)
    public String snapshotPayload(UUID id) {
        return snapshotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report snapshot not found"))
                .getPayload();
    }

    public record SnapshotSummary(UUID id, LocalDate weekStartDate, LocalDate weekEndDate,
            java.time.Instant generatedAt, WeeklyReportSnapshot.Trigger trigger) {

        static SnapshotSummary of(WeeklyReportSnapshot snapshot) {
            return new SnapshotSummary(snapshot.getId(), snapshot.getWeekStartDate(), snapshot.getWeekEndDate(),
                    snapshot.getGeneratedAt(), snapshot.getTrigger());
        }
    }

    @Transactional(readOnly = true)
    public WeeklyReportResponse getWeeklyReport() {
        PortfolioRiskSummary portfolio = riskAnalysisService.getPortfolioSummary();
        LocalDate endDate = portfolio.analysisDate();
        LocalDate startDate = endDate.minusDays(6);
        List<TaskStatusChange> changes = statusChangeRepository.findAllChangedBetween(
                startDate.atStartOfDay(clock.getZone()).toInstant(),
                endDate.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
        // Only the projects in the (already access-filtered) portfolio count towards this caller's activity.
        java.util.Set<UUID> visible = portfolio.projects().stream().map(ProjectRiskSummary::projectId)
                .collect(Collectors.toSet());
        changes = changes.stream().filter(change -> visible.contains(change.getTask().getProject().getId())).toList();
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
