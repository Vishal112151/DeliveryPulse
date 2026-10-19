package com.deliverypulse.jobs;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Scheduled refresh and weekly report jobs.")
public class JobController {

    private final DeliveryJobs jobs;

    public JobController(DeliveryJobs jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    @Operation(summary = "List jobs with their schedule, next run and last result")
    public List<DeliveryJobs.JobStatus> getJobs() {
        return jobs.statuses();
    }

    @PostMapping("/{name}/run")
    @Operation(summary = "Run a job now (refresh or weekly-report)",
            description = "Runs synchronously and returns the result. 409 if the job is already running.")
    public DeliveryJobs.JobRun run(@PathVariable String name) {
        return jobs.runNow(name);
    }
}
