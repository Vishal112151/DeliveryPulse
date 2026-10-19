package com.deliverypulse.risk;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.deliverypulse.dependency.DependencyRepository;
import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.project.ProjectResponse;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.RiskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;
import com.deliverypulse.task.TaskStatusChangeRepository;
import com.deliverypulse.shared.domain.TaskStatus;

@Service
public class RiskAnalysisService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final DependencyRepository dependencyRepository;
    private final RiskRepository riskRepository;
    private final TaskStatusChangeRepository statusChangeRepository;
    private final List<RiskRule> rules;
    private final Clock clock;

    public RiskAnalysisService(ProjectRepository projectRepository, TaskRepository taskRepository,
            DependencyRepository dependencyRepository, RiskRepository riskRepository,
            TaskStatusChangeRepository statusChangeRepository, List<RiskRule> rules, Clock clock) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.riskRepository = riskRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.rules = List.copyOf(rules);
        this.clock = clock;
    }

    @Transactional
    public ProjectRiskSummary analyzeProject(UUID projectId, LocalDate requestedAnalysisDate) {
        LocalDate analysisDate = requestedAnalysisDate == null
                ? LocalDate.now(clock) : requestedAnalysisDate;
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        List<Task> tasks = taskRepository.findAllByProjectId(projectId);
        var dependencies = dependencyRepository.findAllByDependentTaskProjectId(projectId);
        AnalysisContext context = new AnalysisContext(project, analysisDate, tasks, dependencies,
                statusChangeRepository.findAllByProjectId(projectId));
        List<RiskFinding> findings = rules.stream()
                .flatMap(rule -> rule.evaluate(context).stream())
                .toList();

        Instant analyzedAt = clock.instant();
        project.recordAnalysis(analysisDate, analyzedAt);
        List<Risk> existingRisks = riskRepository.findAllByProjectId(projectId);
        Map<String, Risk> risksByFingerprint = new HashMap<>();
        existingRisks.forEach(risk -> risksByFingerprint.put(risk.getFingerprint(), risk));
        Set<String> currentFingerprints = new HashSet<>();
        List<Risk> changedRisks = new ArrayList<>();

        for (RiskFinding finding : findings) {
            currentFingerprints.add(finding.fingerprint());
            Risk existing = risksByFingerprint.get(finding.fingerprint());
            if (existing == null) {
                changedRisks.add(new Risk(project, finding.supportingTasks(), finding.category(),
                        finding.severity(), finding.certainty(), finding.fingerprint(), finding.title(),
                        finding.explanation(), finding.evidence(), finding.recommendedAction(), analyzedAt));
            } else {
                existing.refresh(finding.supportingTasks(), finding.category(), finding.severity(),
                        finding.certainty(), finding.title(), finding.explanation(), finding.evidence(),
                        finding.recommendedAction(), analyzedAt);
                changedRisks.add(existing);
            }
        }
        for (Risk risk : existingRisks) {
            if (risk.getStatus() != RiskStatus.RESOLVED && !currentFingerprints.contains(risk.getFingerprint())) {
                risk.resolve();
                changedRisks.add(risk);
            }
        }
        riskRepository.saveAll(changedRisks);
        return summarize(project, analysisDate, tasks, riskRepository.findAllByProjectId(projectId));
    }

    @Transactional(readOnly = true)
    public ProjectRiskSummary getProjectRisks(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        LocalDate asOfDate = project.getLastAnalysisDate() == null
            ? LocalDate.now(clock) : project.getLastAnalysisDate();
        return summarize(project, asOfDate, taskRepository.findAllByProjectId(projectId),
            riskRepository.findAllByProjectId(projectId));
        }

        @Transactional(readOnly = true)
        public ProjectResponse getProjectSummary(UUID projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        LocalDate asOfDate = project.getLastAnalysisDate() == null
            ? LocalDate.now(clock) : project.getLastAnalysisDate();
        ProjectRiskSummary summary = summarize(project, asOfDate,
            taskRepository.findAllByProjectId(projectId), riskRepository.findAllByProjectId(projectId));
        return toProjectResponse(project, summary);
        }

        @Transactional(readOnly = true)
        public List<ProjectResponse> getProjectSummaries() {
        return projectRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
            .map(project -> {
                LocalDate asOfDate = project.getLastAnalysisDate() == null
                    ? LocalDate.now(clock) : project.getLastAnalysisDate();
                ProjectRiskSummary summary = summarize(project, asOfDate,
                    taskRepository.findAllByProjectId(project.getId()),
                    riskRepository.findAllByProjectId(project.getId()));
                return toProjectResponse(project, summary);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioRiskSummary getPortfolioSummary() {
        LocalDate analysisDate = LocalDate.now(clock);
        Instant analysisTimestamp = clock.instant();
        List<Project> projects = projectRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        List<ProjectRiskSummary> summaries = projects.stream()
            .map(project -> summarize(project,
                project.getLastAnalysisDate() == null ? analysisDate : project.getLastAnalysisDate(),
                taskRepository.findAllByProjectId(project.getId()),
                        riskRepository.findAllByProjectId(project.getId())))
                .toList();
        List<RiskResponse> activeRisks = summaries.stream().flatMap(summary -> summary.risks().stream()).toList();
        int taskCount = summaries.stream().mapToInt(ProjectRiskSummary::taskCount).sum();
        int completedTaskCount = summaries.stream().mapToInt(ProjectRiskSummary::completedTaskCount).sum();
        int incompleteTaskCount = summaries.stream().mapToInt(ProjectRiskSummary::incompleteTaskCount).sum();
        int overdueTaskCount = summaries.stream().mapToInt(ProjectRiskSummary::overdueTaskCount).sum();
        int blockedTaskCount = summaries.stream().mapToInt(ProjectRiskSummary::blockedTaskCount).sum();
        int openDefectCount = summaries.stream().mapToInt(ProjectRiskSummary::openDefectCount).sum();
        return new PortfolioRiskSummary(analysisDate, analysisTimestamp, summaries.size(), taskCount,
            completedTaskCount, incompleteTaskCount, overdueTaskCount, blockedTaskCount, activeRisks.size(),
                countBySeverity(activeRisks), countByCategory(activeRisks), summaries, openDefectCount);
    }

        private ProjectRiskSummary summarize(Project project, LocalDate analysisDate, List<Task> tasks, List<Risk> allRisks) {
        List<Risk> activeRisks = allRisks.stream()
                .filter(risk -> risk.getStatus() != RiskStatus.RESOLVED)
                .sorted(Comparator.comparing(Risk::getSeverity).reversed()
                        .thenComparing(risk -> risk.getCategory().name())
                        .thenComparing(Risk::getTitle)
                        .thenComparing(Risk::getFingerprint))
                .toList();
        List<RiskResponse> responses = activeRisks.stream().map(this::toResponse).toList();
        int completedTaskCount = (int) tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
        int overdueTaskCount = (int) tasks.stream()
            .filter(task -> task.getStatus() != TaskStatus.DONE)
            .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(analysisDate))
            .count();
        int blockedTaskCount = (int) tasks.stream()
            .filter(task -> task.getStatus() == TaskStatus.BLOCKED)
            .count();
        int openDefectCount = (int) tasks.stream()
            .filter(task -> task.isDefect() && task.getStatus() != TaskStatus.DONE)
            .count();
        LocalDate recordedAnalysisDate = project.getLastAnalysisDate() == null
            ? analysisDate : project.getLastAnalysisDate();
        return new ProjectRiskSummary(project.getId(), project.getName(), recordedAnalysisDate,
            project.getLastAnalysisAt(), tasks.size(), completedTaskCount, tasks.size() - completedTaskCount,
            overdueTaskCount, blockedTaskCount, activeRisks.size(), countBySeverity(responses),
            countByCategory(responses), responses, openDefectCount, MilestoneSummary.of(tasks, analysisDate));
    }

        private ProjectResponse toProjectResponse(Project project, ProjectRiskSummary summary) {
        return new ProjectResponse(project.getId(), project.getName(), project.getSourceSheetName(),
            project.getDescription(), project.getCreatedAt(), project.getUpdatedAt(),
            summary.analysisDate(), summary.analysisTimestamp(), summary.taskCount(),
            summary.completedTaskCount(), summary.incompleteTaskCount(), summary.overdueTaskCount(),
            summary.blockedTaskCount(), summary.risksBySeverity(), summary.openDefectCount());
        }

    private RiskResponse toResponse(Risk risk) {
        List<String> taskIds = risk.getSupportingTasks().stream()
            .map(Task::getTaskKey)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new RiskResponse(risk.getId(), risk.getCategory(), risk.getSeverity(), risk.getCertainty(),
                risk.getStatus(), risk.getTitle(), risk.getExplanation(), risk.getEvidence(), taskIds,
                risk.getRecommendedAction(), risk.getDetectedAt());
    }

    private Map<RiskSeverity, Long> countBySeverity(List<RiskResponse> risks) {
        Map<RiskSeverity, Long> counts = new EnumMap<>(RiskSeverity.class);
        for (RiskSeverity severity : RiskSeverity.values()) {
            counts.put(severity, risks.stream().filter(risk -> risk.severity() == severity).count());
        }
        return counts;
    }

    private Map<RiskCategory, Long> countByCategory(List<RiskResponse> risks) {
        Map<RiskCategory, Long> counts = new EnumMap<>(RiskCategory.class);
        for (RiskCategory category : RiskCategory.values()) {
            counts.put(category, risks.stream().filter(risk -> risk.category() == category).count());
        }
        return counts;
    }
}