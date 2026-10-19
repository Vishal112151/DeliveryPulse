package com.deliverypulse.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A weekly report frozen as the JSON the report endpoint returned at that moment. */
@Entity
@Table(name = "weekly_report_snapshots")
public class WeeklyReportSnapshot {

    public enum Trigger {
        SCHEDULED,
        MANUAL
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "week_start_date", nullable = false, updatable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false, updatable = false)
    private LocalDate weekEndDate;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "trigger_type", nullable = false, length = 20, updatable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Trigger trigger;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    protected WeeklyReportSnapshot() {
    }

    WeeklyReportSnapshot(LocalDate weekStartDate, LocalDate weekEndDate, Instant generatedAt, Trigger trigger,
            String payload) {
        this.id = UUID.randomUUID();
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.generatedAt = generatedAt;
        this.trigger = trigger;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public LocalDate getWeekEndDate() {
        return weekEndDate;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public String getPayload() {
        return payload;
    }
}
