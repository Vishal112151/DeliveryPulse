package com.deliverypulse.upload;

import java.time.Instant;
import java.util.UUID;


import com.deliverypulse.shared.domain.ImportStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "import_runs")
public class ImportRun {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @NotBlank
    @Size(max = 20)
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportStatus status;

    @Column(name = "project_count", nullable = false)
    private int projectCount;

    @Column(name = "task_count", nullable = false)
    private int taskCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportRun() {
    }

    public ImportRun(String fileName, String fileType) {
        this.id = UUID.randomUUID();
        this.fileName = fileName;
        this.fileType = fileType;
        this.status = ImportStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void complete(int projectCount, int taskCount, int warningCount) {
        this.projectCount = projectCount;
        this.taskCount = taskCount;
        this.warningCount = warningCount;
        this.status = ImportStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public int getProjectCount() {
        return projectCount;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}