package com.deliverypulse.report;

import java.util.List;

import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.TaskStatusChange;

/** Status changes recorded by imports during the report week. Each change counts once. */
public record WeeklyActivity(
        int tasksAdded,
        int tasksCompleted,
        int tasksBlocked,
        int tasksUnblocked,
        int defectsOpened,
        int defectsClosed) {

    static WeeklyActivity of(List<TaskStatusChange> changes) {
        int added = 0, completed = 0, blocked = 0, unblocked = 0, defectsOpened = 0, defectsClosed = 0;
        for (TaskStatusChange change : changes) {
            boolean defect = change.getTask().isDefect();
            if (change.getFromStatus() == null) {
                added++;
                defectsOpened += defect && change.getToStatus() != TaskStatus.DONE ? 1 : 0;
            }
            if (change.getToStatus() == TaskStatus.DONE && change.getFromStatus() != null) {
                completed++;
                defectsClosed += defect ? 1 : 0;
            }
            blocked += change.getToStatus() == TaskStatus.BLOCKED ? 1 : 0;
            unblocked += change.getFromStatus() == TaskStatus.BLOCKED ? 1 : 0;
        }
        return new WeeklyActivity(added, completed, blocked, unblocked, defectsOpened, defectsClosed);
    }
}
