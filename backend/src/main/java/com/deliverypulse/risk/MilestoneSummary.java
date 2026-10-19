package com.deliverypulse.risk;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;

public record MilestoneSummary(
        String name,
        LocalDate milestoneDate,
        int taskCount,
        int completedTaskCount,
        int blockedTaskCount,
        int openDefectCount,
        Status status) {

    public enum Status {
        COMPLETE,
        MISSED,
        AT_RISK,
        ON_TRACK,
        NO_DATE
    }

    /** Milestones of these tasks, earliest date first; the same tests as MilestoneRiskRule decide AT_RISK. */
    static List<MilestoneSummary> of(List<Task> tasks, LocalDate asOfDate) {
        Map<String, List<Task>> byMilestone = new LinkedHashMap<>();
        tasks.stream().filter(task -> task.getMilestone() != null)
                .forEach(task -> byMilestone.computeIfAbsent(task.getMilestone().toLowerCase(Locale.ROOT),
                        key -> new ArrayList<>()).add(task));
        return byMilestone.values().stream()
                .map(group -> summarize(group, asOfDate))
                .sorted(Comparator.comparing(MilestoneSummary::milestoneDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(MilestoneSummary::name))
                .toList();
    }

    private static MilestoneSummary summarize(List<Task> tasks, LocalDate asOfDate) {
        LocalDate date = tasks.get(0).getMilestoneDate();
        List<Task> open = tasks.stream().filter(task -> task.getStatus() != TaskStatus.DONE).toList();
        int blocked = (int) open.stream().filter(task -> task.getStatus() == TaskStatus.BLOCKED).count();
        int openDefects = (int) open.stream().filter(Task::isDefect).count();
        Status status;
        if (open.isEmpty()) {
            status = Status.COMPLETE;
        } else if (date == null) {
            status = Status.NO_DATE;
        } else if (date.isBefore(asOfDate)) {
            status = Status.MISSED;
        } else if (open.stream().anyMatch(task -> task.getStatus() == TaskStatus.BLOCKED
                || (task.getDueDate() != null && (task.getDueDate().isBefore(asOfDate) || task.getDueDate().isAfter(date))))) {
            status = Status.AT_RISK;
        } else {
            status = Status.ON_TRACK;
        }
        return new MilestoneSummary(tasks.get(0).getMilestone(), date, tasks.size(), tasks.size() - open.size(),
                blocked, openDefects, status);
    }
}
