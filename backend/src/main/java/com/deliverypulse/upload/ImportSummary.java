package com.deliverypulse.upload;

import java.util.List;

public record ImportSummary(
        int projectCount,
        int importedTaskCount,
        List<String> importedProjects,
        List<String> skippedWorksheets,
        List<ImportIssue> warnings,
        List<ImportIssue> errors) {

    public ImportSummary {
        importedProjects = List.copyOf(importedProjects);
        skippedWorksheets = List.copyOf(skippedWorksheets);
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}