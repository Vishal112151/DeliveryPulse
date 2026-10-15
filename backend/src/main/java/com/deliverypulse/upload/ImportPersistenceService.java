package com.deliverypulse.upload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.dependency.DependencyRepository;
import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

@Service
public class ImportPersistenceService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final DependencyRepository dependencyRepository;
    private final ImportRunRepository importRunRepository;

    public ImportPersistenceService(ProjectRepository projectRepository, TaskRepository taskRepository,
            DependencyRepository dependencyRepository, ImportRunRepository importRunRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.importRunRepository = importRunRepository;
    }

    @Transactional
    ImportSummary persist(String fileName, String fileType, ImportDocument document) {
        if (!document.errors().isEmpty()) {
            return document.rejectedSummary();
        }

        List<ImportIssue> errors = new ArrayList<>();
        for (ImportProject project : document.projects()) {
            if (projectRepository.existsByNameIgnoreCase(project.name())) {
                errors.add(new ImportIssue(project.name(), null, null, "PROJECT_ALREADY_EXISTS",
                        "A project with this name already exists; the upload was not imported."));
            }
        }
        if (!errors.isEmpty()) {
            return new ImportSummary(0, 0, List.of(), document.skippedWorksheets(), document.warnings(), errors);
        }

        ImportRun importRun = new ImportRun(fileName, fileType);
        int taskCount = 0;
        List<String> importedProjectNames = new ArrayList<>();
        for (ImportProject importProject : document.projects()) {
            Project project = projectRepository.save(new Project(importProject.name(), importProject.name(), null));
            importedProjectNames.add(project.getName());

            Map<String, Task> tasksByKey = new HashMap<>();
            List<Task> tasks = new ArrayList<>(importProject.tasks().size());
            for (ImportedTask importTask : importProject.tasks()) {
                Task task = new Task(project, importTask.taskKey(), importTask.taskName(),
                        importTask.teamOrModule(), importTask.assignee(), importTask.priority(), importTask.status(),
                        importTask.startDate(), importTask.dueDate(), importTask.blockerText(), importTask.description());
                tasks.add(task);
                tasksByKey.put(importTask.taskKey().toUpperCase(Locale.ROOT), task);
            }
            taskRepository.saveAll(tasks);
            taskCount += tasks.size();

            List<Dependency> dependencies = importProject.dependencies().stream()
                    .map(dependency -> new Dependency(
                            tasksByKey.get(dependency.dependentTaskKey().toUpperCase(Locale.ROOT)),
                            tasksByKey.get(dependency.prerequisiteTaskKey().toUpperCase(Locale.ROOT)),
                            dependency.sourceText()))
                    .toList();
            dependencyRepository.saveAll(dependencies);
        }

        importRun.complete(importedProjectNames.size(), taskCount, document.warnings().size());
        importRunRepository.save(importRun);
        return document.summary(taskCount, importedProjectNames);
    }
}