package com.deliverypulse.risk;

import java.time.LocalDate;
import java.util.List;

import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.project.Project;
import com.deliverypulse.task.Task;

public record AnalysisContext(Project project, LocalDate analysisDate, List<Task> tasks, List<Dependency> dependencies) {

    public AnalysisContext {
        tasks = List.copyOf(tasks);
        dependencies = List.copyOf(dependencies);
    }
}