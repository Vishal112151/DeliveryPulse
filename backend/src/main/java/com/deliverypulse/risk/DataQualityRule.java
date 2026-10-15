package com.deliverypulse.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.DependencyStatus;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;

@Component
public class DataQualityRule implements RiskRule {

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        List<RiskFinding> findings = new ArrayList<>();
        Set<String> knownTaskIds = context.tasks().stream()
                .map(task -> RuleSupport.normalizedTaskId(task.getTaskKey()))
                .collect(Collectors.toSet());
        Map<String, Task> tasksById = context.tasks().stream()
            .collect(Collectors.toMap(task -> RuleSupport.normalizedTaskId(task.getTaskKey()),
                Function.identity(), (first, duplicate) -> first));
        for (Task task : context.tasks()) {
            addTaskInformationFinding(context, task, findings);
            addUnresolvedReferenceFinding(context, task, knownTaskIds, tasksById, findings);
        }

        context.dependencies().stream()
                .filter(dependency -> dependency.getStatus() == DependencyStatus.OPEN)
                .filter(dependency -> dependency.getDependentTask().getProject().getId().equals(context.project().getId()))
                .forEach(dependency -> {
                    if (!dependency.getPrerequisiteTask().getProject().getId().equals(context.project().getId())) {
                        findings.add(RuleSupport.finding(context, RiskCategory.DATA_QUALITY_WARNING,
                                RiskSeverity.LOW, RiskCertainty.DATA_QUALITY,
                                "cross-project-dependency:" + dependency.getId(),
                                List.of(dependency.getDependentTask(), dependency.getPrerequisiteTask()),
                                "Cross-project dependency is unsupported",
                                "Dependency from " + dependency.getDependentTask().getTaskKey() + " references task "
                                        + dependency.getPrerequisiteTask().getTaskKey() + " in another project.",
                                "Task IDs: " + dependency.getDependentTask().getTaskKey() + ", "
                                        + dependency.getPrerequisiteTask().getTaskKey(),
                                "Replace this with an in-project prerequisite or explicitly model cross-project dependencies."));
                    } else if (dependency.getPrerequisiteTask().getStatus() == TaskStatus.DONE) {
                        findings.add(RuleSupport.finding(context, RiskCategory.DATA_QUALITY_WARNING,
                                RiskSeverity.LOW, RiskCertainty.DATA_QUALITY,
                                "open-dependency-completed-prerequisite:" + dependency.getId(),
                                List.of(dependency.getDependentTask(), dependency.getPrerequisiteTask()),
                                "Open dependency points to a completed task",
                                "Dependency from " + dependency.getDependentTask().getTaskKey()
                                        + " remains open although prerequisite " + dependency.getPrerequisiteTask().getTaskKey()
                                        + " is marked Done.",
                                "Task IDs: " + dependency.getDependentTask().getTaskKey() + ", "
                                        + dependency.getPrerequisiteTask().getTaskKey(),
                                "Verify the relationship and mark the dependency resolved if the prerequisite is complete."));
                    }
                });
        return findings;
    }

    private void addTaskInformationFinding(AnalysisContext context, Task task, List<RiskFinding> findings) {
        if (task.getStatus() == TaskStatus.DONE) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (task.getDueDate() == null) {
            missing.add("due date");
        }
        if (task.getAssignee() == null || task.getAssignee().isBlank()) {
            missing.add("assignee");
        }
        if (task.getStartDate() != null && task.getDueDate() != null
                && task.getStartDate().isAfter(task.getDueDate())) {
            missing.add("start date is after due date");
        }
        if (missing.isEmpty()) {
            return;
        }
        findings.add(RuleSupport.taskFinding(context, RiskCategory.DATA_QUALITY_WARNING,
                RiskSeverity.LOW, RiskCertainty.DATA_QUALITY,
                "task-information:" + task.getId(), task,
                "Incomplete task information: " + task.getTaskKey(),
                "Task " + task.getTaskKey() + " has incomplete or inconsistent analysis fields: "
                        + String.join(", ", missing) + ".",
                "Task ID: " + task.getTaskKey() + "; missing/inconsistent fields: " + String.join(", ", missing),
                "Complete the missing fields so delivery status and ownership can be assessed reliably."));
    }

    private void addUnresolvedReferenceFinding(AnalysisContext context, Task task,
            Set<String> knownTaskIds, Map<String, Task> tasksById, List<RiskFinding> findings) {
        List<String> referencedTaskIds = RuleSupport.blockerTokens(task).stream()
            .filter(RuleSupport::isTaskIdToken)
            .map(RuleSupport::normalizedTaskId)
            .distinct()
            .toList();
        List<String> unresolved = RuleSupport.blockerTokens(task).stream()
                .filter(RuleSupport::isTaskIdToken)
            .filter(token -> !knownTaskIds.contains(RuleSupport.normalizedTaskId(token)))
                .distinct()
                .toList();
        if (!unresolved.isEmpty()) {
            findings.add(RuleSupport.taskFinding(context, RiskCategory.DATA_QUALITY_WARNING,
                RiskSeverity.LOW, RiskCertainty.DATA_QUALITY,
                "unresolved-references:" + task.getId(), task,
                "Unresolved dependency reference: " + task.getTaskKey(),
                "Task " + task.getTaskKey() + " contains task-ID-shaped blocker references that do not match a task in this project.",
                "Task ID: " + task.getTaskKey() + "; unresolved references: " + String.join(", ", unresolved),
                "Correct each task ID or keep non-task blocker descriptions as descriptive text."));
        }

        List<Task> referencesWithoutEdges = referencedTaskIds.stream()
            .filter(knownTaskIds::contains)
            .map(tasksById::get)
            .filter(referencedTask -> context.dependencies().stream().noneMatch(dependency ->
                dependency.getDependentTask().getId().equals(task.getId())
                    && dependency.getPrerequisiteTask().getId().equals(referencedTask.getId())))
            .toList();
        if (!referencesWithoutEdges.isEmpty()) {
            List<Task> supportingTasks = new ArrayList<>();
            supportingTasks.add(task);
            supportingTasks.addAll(referencesWithoutEdges);
            String referencedIds = referencesWithoutEdges.stream()
                .map(Task::getTaskKey).collect(Collectors.joining(", "));
            findings.add(RuleSupport.finding(context, RiskCategory.DATA_QUALITY_WARNING,
                RiskSeverity.LOW, RiskCertainty.DATA_QUALITY,
                "dependency-reference-not-linked:" + task.getId(), supportingTasks,
                "Task reference is not normalized: " + task.getTaskKey(),
                "Blocker text names in-project task(s) " + referencedIds
                    + " but no dependency record links them to " + task.getTaskKey() + ".",
                "Task IDs: " + task.getTaskKey() + ", " + referencedIds,
                "Review the references and create or correct the dependency relationships."));
        }
    }
}