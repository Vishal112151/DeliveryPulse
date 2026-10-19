package com.deliverypulse.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.deliverypulse.dependency.DependencyRepository;
import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.RiskStatus;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;
import com.deliverypulse.task.TaskStatusChangeRepository;

@ExtendWith(MockitoExtension.class)
class RiskAnalysisServiceTests {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private DependencyRepository dependencyRepository;

    @Mock
    private RiskRepository riskRepository;

    @Mock
    private TaskStatusChangeRepository statusChangeRepository;

    private final List<Risk> storedRisks = new ArrayList<>();
    private Project project;
    private Task task;
    private RiskAnalysisService service;

    @BeforeEach
    void setUp() {
        project = new Project("Refresh Lifecycle", "Refresh Lifecycle", null);
        task = new Task(project, "REF-1", "Late task", "Core", "Owner", Priority.HIGH,
                TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), null, null);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(taskRepository.findAllByProjectId(project.getId())).thenAnswer(invocation -> List.of(task));
        when(dependencyRepository.findAllByDependentTaskProjectId(project.getId())).thenReturn(List.of());
        when(riskRepository.findAllByProjectId(project.getId())).thenAnswer(invocation -> List.copyOf(storedRisks));
        when(riskRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<Risk> submitted = invocation.getArgument(0);
            for (Risk risk : submitted) {
                storedRisks.removeIf(existing -> existing.getId().equals(risk.getId()));
                storedRisks.add(risk);
            }
            return List.copyOf(storedRisks);
        });

        service = new RiskAnalysisService(projectRepository, taskRepository, dependencyRepository,
                riskRepository, statusChangeRepository, List.of(new OverdueTaskRule(), new BlockedTaskRule(),
                        new CriticalTaskAtRiskRule(), new DependencyRiskRule(), new DataQualityRule()),
                Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void repeatedAnalysisRefreshesOneFindingAndResolvesThenReopensIt() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        ProjectRiskSummary first = service.analyzeProject(project.getId(), date);
        var firstRiskId = first.risks().getFirst().id();

        ProjectRiskSummary repeated = service.analyzeProject(project.getId(), date);
        assertThat(repeated.activeRiskCount()).isEqualTo(1);
        assertThat(repeated.risks()).singleElement().extracting(RiskResponse::id).isEqualTo(firstRiskId);
        assertThat(storedRisks).hasSize(1);

        ReflectionTestUtils.setField(task, "status", TaskStatus.DONE);
        ProjectRiskSummary resolved = service.analyzeProject(project.getId(), date);
        assertThat(resolved.activeRiskCount()).isZero();
        assertThat(storedRisks).singleElement().extracting(Risk::getStatus).isEqualTo(RiskStatus.RESOLVED);

        ReflectionTestUtils.setField(task, "status", TaskStatus.IN_PROGRESS);
        ProjectRiskSummary reopened = service.analyzeProject(project.getId(), date);
        assertThat(reopened.activeRiskCount()).isEqualTo(1);
        assertThat(reopened.risks()).singleElement().extracting(RiskResponse::id).isEqualTo(firstRiskId);
        assertThat(storedRisks).hasSize(1);
    }

    @Test
    void returnsActiveRisksMostSevereFirst() {
        // Overdue (HIGH), critical-at-risk (CRITICAL) and missing-assignee (LOW) findings on one task.
        task = new Task(project, "REF-2", "Late critical task", "Core", null, Priority.CRITICAL,
                TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), null, null);

        ProjectRiskSummary summary = service.analyzeProject(project.getId(), LocalDate.of(2026, 10, 15));

        assertThat(summary.risks()).extracting(RiskResponse::severity).containsExactly(
                RiskSeverity.CRITICAL, RiskSeverity.HIGH, RiskSeverity.LOW);
    }

    @Test
    void omittedAnalysisDateUsesTheInjectedClock() {
        ProjectRiskSummary summary = service.analyzeProject(project.getId(), null);

        assertThat(summary.analysisDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(summary.activeRiskCount()).isEqualTo(1);
    }
}