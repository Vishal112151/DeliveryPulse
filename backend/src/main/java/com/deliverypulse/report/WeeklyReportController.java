package com.deliverypulse.report;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Reports", description = "Computed project delivery status reports.")
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    public WeeklyReportController(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    @GetMapping("/api/reports/weekly")
    @Operation(summary = "Get the weekly portfolio status snapshot",
            description = "Returns current project/task/risk totals, plus the task status changes that imports recorded during the seven-day reporting window.")
    public WeeklyReportResponse getWeeklyReport() {
        return weeklyReportService.getWeeklyReport();
    }

    @GetMapping("/api/reports/weekly/snapshots")
    @Operation(summary = "List saved weekly reports, newest first",
            description = "Snapshots are saved by the weekly scheduled job or by running it manually.")
    public List<WeeklyReportService.SnapshotSummary> listSnapshots() {
        return weeklyReportService.listSnapshots();
    }

    @GetMapping(value = "/api/reports/weekly/snapshots/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a saved weekly report", description = "Same shape as GET /api/reports/weekly.")
    public String getSnapshot(@PathVariable UUID id) {
        return weeklyReportService.snapshotPayload(id);
    }
}