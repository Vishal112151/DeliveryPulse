package com.deliverypulse.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the jobs on their cron schedules in APP_TIME_ZONE. SCHEDULE_ENABLED=false turns this off. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class JobScheduler {

    private final DeliveryJobs jobs;

    public JobScheduler(DeliveryJobs jobs) {
        this.jobs = jobs;
    }

    @Scheduled(cron = "${app.schedule.refresh-cron}", zone = "${app.time-zone:UTC}")
    void refresh() {
        jobs.refresh(false);
    }

    @Scheduled(cron = "${app.schedule.weekly-report-cron}", zone = "${app.time-zone:UTC}")
    void saveWeeklyReport() {
        jobs.saveWeeklyReport(false);
    }
}
