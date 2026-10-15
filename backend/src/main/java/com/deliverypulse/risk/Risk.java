package com.deliverypulse.risk;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.deliverypulse.project.Project;
import com.deliverypulse.shared.domain.RiskCategory;
import com.deliverypulse.shared.domain.RiskCertainty;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.RiskStatus;
import com.deliverypulse.task.Task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "risks")
public class Risk {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_risks_project"))
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_risks_task"))
    private Task task;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "risk_supporting_tasks",
            joinColumns = @JoinColumn(name = "risk_id"),
            inverseJoinColumns = @JoinColumn(name = "task_id"))
    private Set<Task> supportingTasks = new LinkedHashSet<>();

    @NotBlank
    @Column(nullable = false, unique = true, length = 500)
    private String fingerprint;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RiskCategory category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskSeverity severity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskCertainty certainty;

    @NotBlank
    @Size(max = 240)
    @Column(nullable = false, length = 240)
    private String title;

    @NotBlank
    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(columnDefinition = "text")
    private String evidence;

    @Column(name = "recommended_action", columnDefinition = "text")
    private String recommendedAction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskStatus status;

    @NotNull
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Risk() {
    }

    public Risk(Project project, Collection<Task> supportingTasks, RiskCategory category, RiskSeverity severity,
            RiskCertainty certainty, String fingerprint, String title, String explanation,
            String evidence, String recommendedAction, Instant detectedAt) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.supportingTasks = new LinkedHashSet<>(supportingTasks);
        this.task = this.supportingTasks.stream().findFirst().orElse(null);
        this.category = category;
        this.severity = severity;
        this.certainty = certainty;
        this.fingerprint = fingerprint;
        this.title = title;
        this.explanation = explanation;
        this.evidence = evidence;
        this.recommendedAction = recommendedAction;
        this.status = RiskStatus.OPEN;
        this.detectedAt = detectedAt;
    }

    public void refresh(Collection<Task> supportingTasks, RiskCategory category, RiskSeverity severity,
            RiskCertainty certainty, String title, String explanation, String evidence,
            String recommendedAction, Instant detectedAt) {
        this.supportingTasks.clear();
        this.supportingTasks.addAll(supportingTasks);
        this.task = this.supportingTasks.stream().findFirst().orElse(null);
        this.category = category;
        this.severity = severity;
        this.certainty = certainty;
        this.title = title;
        this.explanation = explanation;
        this.evidence = evidence;
        this.recommendedAction = recommendedAction;
        this.detectedAt = detectedAt;
        if (this.status == RiskStatus.RESOLVED) {
            this.status = RiskStatus.OPEN;
        }
    }

    public void resolve() {
        this.status = RiskStatus.RESOLVED;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Task getTask() {
        return task;
    }

    public Set<Task> getSupportingTasks() {
        return Set.copyOf(supportingTasks);
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public RiskCategory getCategory() {
        return category;
    }

    public RiskSeverity getSeverity() {
        return severity;
    }

    public RiskCertainty getCertainty() {
        return certainty;
    }

    public String getTitle() {
        return title;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public RiskStatus getStatus() {
        return status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}