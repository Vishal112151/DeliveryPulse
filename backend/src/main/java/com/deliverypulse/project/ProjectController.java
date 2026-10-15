package com.deliverypulse.project;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.TaskPageResponse;
import com.deliverypulse.task.TaskSortDirection;
import com.deliverypulse.task.TaskSortField;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Browse projects and their tasks.")
public class ProjectController {

    private final ProjectApiService projectApiService;

    public ProjectController(ProjectApiService projectApiService) {
        this.projectApiService = projectApiService;
    }

    @GetMapping
    @Operation(summary = "List projects with current delivery metrics")
    public List<ProjectResponse> getProjects() {
        return projectApiService.getProjects();
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project details and delivery metrics")
    public ProjectResponse getProject(@PathVariable UUID projectId) {
        return projectApiService.getProject(projectId);
    }

    @GetMapping("/{projectId}/tasks")
    @Operation(summary = "Get filtered, paginated project tasks",
            description = "Assignee matching is case-insensitive and exact. Overdue means due before analysisDate and status not Done.")
    public TaskPageResponse getProjectTasks(
            @PathVariable UUID projectId,
            @Parameter(description = "Filter by task status") @RequestParam(required = false) TaskStatus status,
            @Parameter(description = "Filter by priority") @RequestParam(required = false) Priority priority,
            @Parameter(description = "Exact case-insensitive assignee filter")
            @RequestParam(required = false) @Size(max = 160) String assignee,
            @Parameter(description = "Filter overdue tasks; due today is not overdue")
            @RequestParam(required = false) Boolean overdue,
            @Parameter(description = "Date used to evaluate overdue state, yyyy-MM-dd; defaults to the project's last analysis date, or today if never analyzed")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate analysisDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort field: TASK_ID, TASK_NAME, STATUS, PRIORITY, ASSIGNEE, or DUE_DATE")
            @RequestParam(defaultValue = "TASK_ID") TaskSortField sortBy,
            @Parameter(description = "Sort direction: ASC or DESC")
            @RequestParam(defaultValue = "ASC") TaskSortDirection sortDirection) {
        return projectApiService.getProjectTasks(projectId, status, priority, assignee, overdue,
                analysisDate, page, size, sortBy, sortDirection);
    }
}