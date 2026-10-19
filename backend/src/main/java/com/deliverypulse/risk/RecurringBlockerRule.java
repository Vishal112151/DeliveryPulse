package com.deliverypulse.risk;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.TaskStatusChange;

/** A task that has entered Blocked more than once, according to the import history, and is not Done. */
@Component
public class RecurringBlockerRule implements RiskRule {

    static final int RECURRING_AT = 2;
    static final int CRITICAL_AT = 3;

    @Override
    public List<RiskFinding> evaluate(AnalysisContext context) {
        Map<UUID, List<LocalDate>> blockedDates = context.statusChanges().stream()
                .filter(change -> change.getToStatus() == TaskStatus.BLOCKED)
                .collect(Collectors.groupingBy(change -> change.getTask().getId(),
                        Collectors.mapping(RecurringBlockerRule::changedOn, Collectors.toList())));
        return context.tasks().stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .filter(task -> blockedDates.getOrDefault(task.getId(), List.of()).size() >= RECURRING_AT)
                .map(task -> {
                    List<LocalDate> dates = blockedDates.get(task.getId()).stream().sorted().toList();
                    return RuleSupport.taskFinding(context, RiskCategory.RECURRING_BLOCKER,
                            dates.size() >= CRITICAL_AT ? RiskSeverity.CRITICAL : RiskSeverity.HIGH,
                            RiskCertainty.CONFIRMED, task.getId().toString(), task,
                            "Recurring blocker: " + task.getTaskKey(),
                            RuleSupport.taskReference(task) + " has been blocked " + dates.size()
                                    + " times. Status now: " + task.getStatus() + ".",
                            "Task ID: " + task.getTaskKey() + "; entered Blocked on (UTC): "
                                    + dates.stream().map(LocalDate::toString).collect(Collectors.joining(", "))
                                    + "; current blocker text: " + task.getBlockerText(),
                            "Find the root cause behind the repeated blocks and agree a lasting fix with its owner, "
                                    + "rather than clearing the blocker again.");
                })
                .toList();
    }

    private static LocalDate changedOn(TaskStatusChange change) {
        return LocalDate.ofInstant(change.getChangedAt(), ZoneOffset.UTC);
    }
}
