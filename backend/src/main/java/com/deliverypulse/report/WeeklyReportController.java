package com.deliverypulse.report;

import org.springframework.web.bind.annotation.GetMapping;
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
}