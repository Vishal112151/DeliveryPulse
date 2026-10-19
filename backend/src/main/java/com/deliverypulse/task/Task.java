package com.deliverypulse.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.deliverypulse.project.Project;
import com.deliverypulse.shared.domain.IssueType;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "tasks", uniqueConstraints = @UniqueConstraint(
        name = "uk_tasks_project_task_key", columnNames = {"project_id", "task_key"}))
public class Task {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tasks_project"))
    private Project project;

    @NotBlank
    @Size(max = 100)
    @Column(name = "task_key", nullable = false, length = 100)
    private String taskKey;

    @NotBlank
    @Size(max = 300)
    @Column(name = "task_name", nullable = false, length = 300)
    private String taskName;

    @Size(max = 160)
    @Column(name = "team_or_module", length = 160)
    private String teamOrModule;

    @Size(max = 160)
    @Column(length = 160)
    private String assignee;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "blocker_text", columnDefinition = "text")
    private String blockerText;

    @Column(columnDefinition = "text")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 20)
    private IssueType issueType = IssueType.TASK;

    @Size(max = 160)
    @Column(length = 160)
    private String milestone;

    @Column(name = "milestone_date")
    private LocalDate milestoneDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
    }

    public Task(Project project, String taskKey, String taskName, Priority priority, TaskStatus status) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.taskKey = taskKey;
        this.taskName = taskName;
        this.priority = priority;
        this.status = status;
    }

    public Task(Project project, String taskKey, String taskName, String teamOrModule, String assignee,
            Priority priority, TaskStatus status, LocalDate startDate, LocalDate dueDate,
            String blockerText, String description) {
        this(project, taskKey, taskName, priority, status);
        this.teamOrModule = teamOrModule;
        this.assignee = assignee;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.blockerText = blockerText;
        this.description = description;
    }

    public Task(Project project, String taskKey, String taskName, String teamOrModule, String assignee,
            Priority priority, TaskStatus status, LocalDate startDate, LocalDate dueDate,
            String blockerText, String description, IssueType issueType, String milestone,
            LocalDate milestoneDate) {
        this(project, taskKey, taskName, teamOrModule, assignee, priority, status, startDate, dueDate,
                blockerText, description);
        this.issueType = issueType == null ? IssueType.TASK : issueType;
        this.milestone = milestone;
        this.milestoneDate = milestoneDate;
    }

    /** Overwrites every imported field; the task key and project stay the same. */
    public void applyImport(String taskName, String teamOrModule, String assignee, Priority priority,
            TaskStatus status, LocalDate startDate, LocalDate dueDate, String blockerText, String description,
            IssueType issueType, String milestone, LocalDate milestoneDate) {
        this.taskName = taskName;
        this.teamOrModule = teamOrModule;
        this.assignee = assignee;
        this.priority = priority;
        this.status = status;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.blockerText = blockerText;
        this.description = description;
        this.issueType = issueType == null ? IssueType.TASK : issueType;
        this.milestone = milestone;
        this.milestoneDate = milestoneDate;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTeamOrModule() {
        return teamOrModule;
    }

    public String getAssignee() {
        return assignee;
    }

    public Priority getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getBlockerText() {
        return blockerText;
    }

    public String getDescription() {
        return description;
    }

    public IssueType getIssueType() {
        return issueType;
    }

    public boolean isDefect() {
        return issueType == IssueType.DEFECT;
    }

    public String getMilestone() {
        return milestone;
    }

    public LocalDate getMilestoneDate() {
        return milestoneDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}