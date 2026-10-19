package com.deliverypulse.jobs;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.report.WeeklyReportService;
import com.deliverypulse.report.WeeklyReportSnapshot;
import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.upload.ImportSummary;
import com.deliverypulse.upload.JiraSyncService;

/**
 * The two automated jobs. {@link JobScheduler} runs them on their cron schedules; admins can also run them
 * on demand. Last results are kept in memory, so they reset when the backend restarts.
 */
@Service
public class DeliveryJobs {

    public static final String REFRESH = "refresh";
    public static final String WEEKLY_REPORT = "weekly-report";
    private static final Logger logger = LoggerFactory.getLogger(DeliveryJobs.class);

    private final JiraSyncService jiraSyncService;
    private final ProjectRepository projectRepository;
    private final RiskAnalysisService riskAnalysisService;
    private final WeeklyReportService weeklyReportService;
    private final Clock clock;
    private final boolean schedulingEnabled;
    private final Map<String, String> crons;
    private final Map<String, JobRun> lastRuns = new ConcurrentHashMap<>();
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();

    public DeliveryJobs(JiraSyncService jiraSyncService, ProjectRepository projectRepository,
            RiskAnalysisService riskAnalysisService, WeeklyReportService weeklyReportService, Clock clock,
            @Value("${app.schedule.enabled:true}") boolean schedulingEnabled,
            @Value("${app.schedule.refresh-cron}") String refreshCron,
            @Value("${app.schedule.weekly-report-cron}") String weeklyReportCron) {
        this.jiraSyncService = jiraSyncService;
        this.projectRepository = projectRepository;
        this.riskAnalysisService = riskAnalysisService;
        this.weeklyReportService = weeklyReportService;
        this.clock = clock;
        this.schedulingEnabled = schedulingEnabled;
        this.crons = Map.of(REFRESH, refreshCron, WEEKLY_REPORT, weeklyReportCron);
    }

    /** Syncs Jira when it is configured, then analyzes every project as of today. */
    public JobRun refresh(boolean manual) {
        return run(REFRESH, manual, () -> {
            List<String> parts = new ArrayList<>();
            if (jiraSyncService.status().configured()) {
                ImportSummary summary = jiraSyncService.sync();
                parts.add("Jira sync: " + summary.projectCount() + " projects, " + summary.importedTaskCount() + " tasks");
            } else {
                parts.add("Jira not configured, sync skipped");
            }
            int analyzed = 0;
            List<String> failed = new ArrayList<>();
            for (Project project : projectRepository.findAll()) {
                try {
                    riskAnalysisService.analyzeProject(project.getId(), null);
                    analyzed++;
                } catch (RuntimeException exception) {
                    logger.warn("Scheduled analysis failed: project={}", project.getId(), exception);
                    failed.add(project.getName());
                }
            }
            parts.add("analyzed " + analyzed + " projects");
            if (!failed.isEmpty()) {
                throw new IllegalStateException(String.join("; ", parts) + "; analysis failed for " + String.join(", ", failed));
            }
            return String.join("; ", parts);
        });
    }

    /** Saves the current weekly report as a snapshot. */
    public JobRun saveWeeklyReport(boolean manual) {
        return run(WEEKLY_REPORT, manual, () -> {
            WeeklyReportService.SnapshotSummary snapshot = weeklyReportService.saveSnapshot(
                    manual ? WeeklyReportSnapshot.Trigger.MANUAL : WeeklyReportSnapshot.Trigger.SCHEDULED);
            return "Saved report for " + snapshot.weekStartDate() + " to " + snapshot.weekEndDate();
        });
    }

    public JobRun runNow(String name) {
        return switch (name) {
            case REFRESH -> refresh(true);
            case WEEKLY_REPORT -> saveWeeklyReport(true);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + name);
        };
    }

    public List<JobStatus> statuses() {
        return List.of(status(REFRESH, "Sync Jira (if configured) and analyze all projects"),
                status(WEEKLY_REPORT, "Save the weekly report as a snapshot"));
    }

    private JobStatus status(String name, String description) {
        String cron = crons.get(name);
        ZonedDateTime next = schedulingEnabled
                ? CronExpression.parse(cron).next(ZonedDateTime.now(clock)) : null;
        return new JobStatus(name, description, cron, schedulingEnabled, next == null ? null : next.toInstant(),
                Boolean.TRUE.equals(running.get(name)), lastRuns.get(name));
    }

    private JobRun run(String name, boolean manual, Supplier<String> work) {
        if (running.putIfAbsent(name, Boolean.TRUE) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job " + name + " is already running.");
        }
        Instant startedAt = clock.instant();
        JobRun result;
        try {
            String message = work.get();
            result = new JobRun(startedAt, clock.instant(), manual, true, message);
            logger.info("Job completed: job={}, manual={}, result={}", name, manual, message);
        } catch (RuntimeException exception) {
            String message = exception instanceof ResponseStatusException status && status.getReason() != null
                    ? status.getReason() : exception.getMessage();
            result = new JobRun(startedAt, clock.instant(), manual, false, message);
            logger.warn("Job failed: job={}, manual={}, reason={}", name, manual, message);
        } finally {
            running.remove(name);
        }
        lastRuns.put(name, result);
        return result;
    }

    public record JobRun(Instant startedAt, Instant finishedAt, boolean manual, boolean succeeded, String message) {
    }

    public record JobStatus(String name, String description, String cron, boolean scheduled, Instant nextRunAt,
            boolean running, JobRun lastRun) {
    }
}
