package com.deliverypulse.risk;

import java.time.LocalDate;
import java.util.List;

import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.project.Project;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskStatusChange;

public record AnalysisContext(Project project, LocalDate analysisDate, List<Task> tasks, List<Dependency> dependencies,
        List<TaskStatusChange> statusChanges) {

    public AnalysisContext {
        tasks = List.copyOf(tasks);
        dependencies = List.copyOf(dependencies);
        statusChanges = List.copyOf(statusChanges);
    }

    public AnalysisContext(Project project, LocalDate analysisDate, List<Task> tasks, List<Dependency> dependencies) {
        this(project, analysisDate, tasks, dependencies, List.of());
    }
}
