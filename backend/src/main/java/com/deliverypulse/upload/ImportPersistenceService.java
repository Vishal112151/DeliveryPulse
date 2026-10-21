package com.deliverypulse.upload;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.auth.ProjectAccess;
import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.dependency.DependencyRepository;
import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;
import com.deliverypulse.task.TaskStatusChange;
import com.deliverypulse.task.TaskStatusChangeRepository;

@Service
public class ImportPersistenceService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final DependencyRepository dependencyRepository;
    private final ImportRunRepository importRunRepository;
    private final TaskStatusChangeRepository statusChangeRepository;
    private final ProjectAccess access;
    private final Clock clock;

    public ImportPersistenceService(ProjectRepository projectRepository, TaskRepository taskRepository,
            DependencyRepository dependencyRepository, ImportRunRepository importRunRepository,
            TaskStatusChangeRepository statusChangeRepository, ProjectAccess access, Clock clock) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.importRunRepository = importRunRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Saves the parsed projects. A project whose name already exists is rejected unless
     * {@code updateExisting} is set; then its tasks are updated in place by Task ID and every status
     * change is recorded in the task status history.
     */
    @Transactional
    ImportSummary persist(String fileName, String fileType, ImportDocument document, boolean updateExisting) {
        if (!document.errors().isEmpty()) {
            return document.rejectedSummary();
        }

        List<ImportIssue> errors = new ArrayList<>();
        for (ImportProject project : document.projects()) {
            Optional<Project> existing = projectRepository.findByNameIgnoreCase(project.name());
            if (existing.isEmpty()) {
                continue;
            }
            if (!updateExisting) {
                errors.add(new ImportIssue(project.name(), null, null, "PROJECT_ALREADY_EXISTS",
                        "A project with this name already exists; the upload was not imported."));
            } else if (!access.canSee(existing.get().getId())) {
                errors.add(new ImportIssue(project.name(), null, null, "PROJECT_NOT_ACCESSIBLE",
                        "A project with this name exists but is not assigned to you; ask an admin."));
            }
        }
        if (!errors.isEmpty()) {
            return new ImportSummary(0, 0, List.of(), document.skippedWorksheets(), document.warnings(), errors);
        }

        Instant importedAt = clock.instant();
        ImportRun importRun = new ImportRun(fileName, fileType);
        List<ImportIssue> warnings = new ArrayList<>(document.warnings());
        int taskCount = 0;
        List<String> importedProjectNames = new ArrayList<>();
        for (ImportProject importProject : document.projects()) {
            Optional<Project> existing = projectRepository.findByNameIgnoreCase(importProject.name());
            Project project = existing.orElseGet(() -> {
                Project created = projectRepository.save(new Project(importProject.name(), importProject.name(), null));
                access.grantToCurrentUser(created);
                return created;
            });
            importedProjectNames.add(project.getName());

            Map<String, Task> tasksByKey = new HashMap<>();
            if (existing.isPresent()) {
                taskRepository.findAllByProjectId(project.getId())
                        .forEach(task -> tasksByKey.put(key(task.getTaskKey()), task));
                // Dependencies are rebuilt from this file. Flush the deletes before the inserts so a
                // dependency that is still listed does not collide with its old row.
                dependencyRepository.deleteAll(dependencyRepository.findAllByDependentTaskProjectId(project.getId()));
                dependencyRepository.flush();
            }
            Set<String> keysInFile = new HashSet<>();
            List<Task> tasks = new ArrayList<>(importProject.tasks().size());
            List<TaskStatusChange> changes = new ArrayList<>();
            for (ImportedTask importTask : importProject.tasks()) {
                String key = key(importTask.taskKey());
                keysInFile.add(key);
                Task task = tasksByKey.get(key);
                if (task == null) {
                    task = new Task(project, importTask.taskKey(), importTask.taskName(),
                            importTask.teamOrModule(), importTask.assignee(), importTask.priority(), importTask.status(),
                            importTask.startDate(), importTask.dueDate(), importTask.blockerText(), importTask.description(),
                            importTask.issueType(), importTask.milestone(), importTask.milestoneDate());
                    task.setWorkType(importTask.workType());
                    tasksByKey.put(key, task);
                    changes.add(new TaskStatusChange(task, null, task.getStatus(), importedAt));
                } else {
                    var previousStatus = task.getStatus();
                    task.applyImport(importTask.taskName(), importTask.teamOrModule(), importTask.assignee(),
                            importTask.priority(), importTask.status(), importTask.startDate(), importTask.dueDate(),
                            importTask.blockerText(), importTask.description(), importTask.issueType(),
                            importTask.milestone(), importTask.milestoneDate());
                    task.setWorkType(importTask.workType());
                    if (previousStatus != task.getStatus()) {
                        changes.add(new TaskStatusChange(task, previousStatus, task.getStatus(), importedAt));
                    }
                }
                tasks.add(task);
            }
            taskRepository.saveAll(tasks);
            statusChangeRepository.saveAll(changes);
            taskCount += tasks.size();

            // ponytail: tasks missing from the file are kept as they were, since risks and history point
            // at them. Add a "removed" state if sources start dropping tasks regularly.
            tasksByKey.forEach((key, task) -> {
                if (!keysInFile.contains(key)) {
                    warnings.add(new ImportIssue(project.getName(), null, "Task ID", "TASK_NOT_IN_UPLOAD",
                            "Task '" + task.getTaskKey() + "' is not in this file and was left unchanged."));
                }
            });

            List<Dependency> dependencies = importProject.dependencies().stream()
                    .map(dependency -> new Dependency(
                            tasksByKey.get(key(dependency.dependentTaskKey())),
                            tasksByKey.get(key(dependency.prerequisiteTaskKey())),
                            dependency.sourceText()))
                    .toList();
            dependencyRepository.saveAll(dependencies);
        }

        importRun.complete(importedProjectNames.size(), taskCount, warnings.size());
        importRunRepository.save(importRun);
        return new ImportSummary(importedProjectNames.size(), taskCount, importedProjectNames,
                document.skippedWorksheets(), warnings, List.of());
    }

    private static String key(String taskKey) {
        return taskKey.trim().toUpperCase(Locale.ROOT);
    }
}
