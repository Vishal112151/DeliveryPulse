package com.deliverypulse.upload;

import java.util.List;

record ImportDocument(
        List<ImportProject> projects,
        List<String> skippedWorksheets,
        List<ImportIssue> warnings,
        List<ImportIssue> errors) {

    ImportDocument {
        projects = List.copyOf(projects);
        skippedWorksheets = List.copyOf(skippedWorksheets);
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
    }

    ImportSummary summary(int importedTaskCount, List<String> importedProjects) {
        return new ImportSummary(importedProjects.size(), importedTaskCount, importedProjects,
                skippedWorksheets, warnings, errors);
    }

    ImportSummary rejectedSummary() {
        return summary(0, List.of());
    }
}

record ImportProject(String name, List<ImportedTask> tasks, List<ImportedDependency> dependencies) {

    ImportProject {
        tasks = List.copyOf(tasks);
        dependencies = List.copyOf(dependencies);
    }
}

record ImportedTask(
        int row,
        String taskKey,
        String taskName,
        String teamOrModule,
        String assignee,
        com.deliverypulse.shared.domain.Priority priority,
        com.deliverypulse.shared.domain.TaskStatus status,
        java.time.LocalDate startDate,
        java.time.LocalDate dueDate,
        String blockerText,
        String description,
        com.deliverypulse.shared.domain.IssueType issueType,
        String milestone,
        java.time.LocalDate milestoneDate,
        /** The source's own type name (Jira issue type or the upload's Type value), for display. */
        String workType) {
}

record ImportedDependency(String dependentTaskKey, String prerequisiteTaskKey, String sourceText) {
}