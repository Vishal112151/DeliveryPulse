package com.deliverypulse.dependency;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.deliverypulse.shared.domain.DependencyStatus;
import com.deliverypulse.task.Task;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "dependencies", uniqueConstraints = @UniqueConstraint(
        name = "uk_dependencies_task_pair", columnNames = {"dependent_task_id", "prerequisite_task_id"}))
public class Dependency {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dependent_task_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_dependencies_dependent_task"))
    private Task dependentTask;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_task_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_dependencies_prerequisite_task"))
    private Task prerequisiteTask;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DependencyStatus status;

    @Size(max = 500)
    @Column(name = "source_text", length = 500)
    private String sourceText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Dependency() {
    }

    public Dependency(Task dependentTask, Task prerequisiteTask, String sourceText) {
        this.id = UUID.randomUUID();
        this.dependentTask = dependentTask;
        this.prerequisiteTask = prerequisiteTask;
        this.status = DependencyStatus.OPEN;
        this.sourceText = sourceText;
    }

    public UUID getId() {
        return id;
    }

    public Task getDependentTask() {
        return dependentTask;
    }

    public Task getPrerequisiteTask() {
        return prerequisiteTask;
    }

    public DependencyStatus getStatus() {
        return status;
    }

    public String getSourceText() {
        return sourceText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}