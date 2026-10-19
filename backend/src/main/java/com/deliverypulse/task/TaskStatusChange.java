package com.deliverypulse.task;

import java.time.Instant;
import java.util.UUID;

import com.deliverypulse.shared.domain.TaskStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A status a task entered, recorded by an import. fromStatus is null when the task first appeared. */
@Entity
@Table(name = "task_status_history")
public class TaskStatusChange {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_task_status_history_task"))
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private TaskStatus toStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected TaskStatusChange() {
    }

    public TaskStatusChange(Task task, TaskStatus fromStatus, TaskStatus toStatus, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.task = task;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public TaskStatus getFromStatus() {
        return fromStatus;
    }

    public TaskStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
