package com.deliverypulse.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(name = "normalized_name", nullable = false, unique = true, length = 160)
    private String normalizedName;

    @Size(max = 160)
    @Column(name = "source_sheet_name", length = 160)
    private String sourceSheetName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "last_analysis_date")
    private LocalDate lastAnalysisDate;

    @Column(name = "last_analysis_at")
    private Instant lastAnalysisAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {
    }

    public Project(String name, String sourceSheetName, String description) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.normalizedName = normalizeName(name);
        this.sourceSheetName = sourceSheetName;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @PrePersist
    @PreUpdate
    private void updateNormalizedName() {
        this.normalizedName = normalizeName(name);
    }

    private String normalizeName(String projectName) {
        return projectName == null ? null : projectName.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String getSourceSheetName() {
        return sourceSheetName;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getLastAnalysisDate() {
        return lastAnalysisDate;
    }

    public Instant getLastAnalysisAt() {
        return lastAnalysisAt;
    }

    public void recordAnalysis(LocalDate analysisDate, Instant analysisAt) {
        this.lastAnalysisDate = analysisDate;
        this.lastAnalysisAt = analysisAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}