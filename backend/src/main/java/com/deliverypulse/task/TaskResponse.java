package com.deliverypulse.task;

import java.time.LocalDate;
import java.util.UUID;

import com.deliverypulse.shared.domain.IssueType;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;

public record TaskResponse(
        UUID id,
        UUID projectId,
        String taskId,
        String taskName,
        String teamOrModule,
        String assignee,
        Priority priority,
        TaskStatus status,
        LocalDate startDate,
        LocalDate dueDate,
        boolean overdue,
        String blockerText,
        String description,
        IssueType issueType,
        String milestone,
        LocalDate milestoneDate,
        String workType) {
}