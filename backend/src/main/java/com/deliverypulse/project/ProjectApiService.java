package com.deliverypulse.project;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskPageResponse;
import com.deliverypulse.task.TaskRepository;
import com.deliverypulse.task.TaskResponse;
import com.deliverypulse.task.TaskSortDirection;
import com.deliverypulse.task.TaskSortField;

import jakarta.persistence.criteria.Predicate;

@Service
public class ProjectApiService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final RiskAnalysisService riskAnalysisService;
    private final Clock clock;

    public ProjectApiService(ProjectRepository projectRepository, TaskRepository taskRepository,
            RiskAnalysisService riskAnalysisService, Clock clock) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.riskAnalysisService = riskAnalysisService;
        this.clock = clock;
    }

    public List<ProjectResponse> getProjects() {
        return riskAnalysisService.getProjectSummaries();
    }

    public ProjectResponse getProject(UUID projectId) {
        return riskAnalysisService.getProjectSummary(projectId);
    }

    @Transactional(readOnly = true)
    public TaskPageResponse getProjectTasks(UUID projectId, TaskStatus status, Priority priority,
            String assignee, Boolean overdue, LocalDate requestedAnalysisDate, int page, int size,
            TaskSortField sortBy, TaskSortDirection sortDirection) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        // Same as-of date as the project's metrics and risks, so the overdue flags agree with them.
        LocalDate analysisDate = requestedAnalysisDate != null ? requestedAnalysisDate
                : project.getLastAnalysisDate() != null ? project.getLastAnalysisDate() : LocalDate.now(clock);
        Specification<Task> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("project").get("id"), projectId);
        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }
        if (priority != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("priority"), priority));
        }
        if (assignee != null && !assignee.isBlank()) {
            String normalizedAssignee = assignee.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(criteriaBuilder.lower(root.get("assignee")), normalizedAssignee));
        }
        if (overdue != null) {
            specification = specification.and((root, query, criteriaBuilder) -> {
                Predicate incomplete = criteriaBuilder.notEqual(root.get("status"), TaskStatus.DONE);
                Predicate dueBeforeAnalysisDate = criteriaBuilder.lessThan(root.get("dueDate"), analysisDate);
                Predicate overduePredicate = criteriaBuilder.and(incomplete, dueBeforeAnalysisDate);
                if (overdue) {
                    return overduePredicate;
                }
                return criteriaBuilder.or(criteriaBuilder.not(overduePredicate),
                        criteriaBuilder.isNull(root.get("dueDate")));
            });
        }

        TaskSortField actualSortBy = sortBy == null ? TaskSortField.TASK_ID : sortBy;
        TaskSortDirection actualSortDirection = sortDirection == null ? TaskSortDirection.ASC : sortDirection;
        PageRequest pageRequest = PageRequest.of(page, size,
            Sort.by(actualSortDirection.toSortDirection(), actualSortBy.property()));
        Page<Task> results = taskRepository.findAll(specification, pageRequest);
        List<TaskResponse> tasks = results.getContent().stream()
                .map(task -> toResponse(task, analysisDate))
                .toList();
        return new TaskPageResponse(tasks, results.getNumber(), results.getSize(), results.getTotalElements(),
            results.getTotalPages(), analysisDate, clock.instant(), actualSortBy, actualSortDirection);
    }

    private TaskResponse toResponse(Task task, LocalDate analysisDate) {
        boolean isOverdue = task.getStatus() != TaskStatus.DONE
                && task.getDueDate() != null
                && task.getDueDate().isBefore(analysisDate);
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getTaskKey(), task.getTaskName(),
                task.getTeamOrModule(), task.getAssignee(), task.getPriority(), task.getStatus(),
                task.getStartDate(), task.getDueDate(), isOverdue, task.getBlockerText(), task.getDescription());
    }
}