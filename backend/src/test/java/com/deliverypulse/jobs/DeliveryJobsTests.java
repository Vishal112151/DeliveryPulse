package com.deliverypulse.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
@ActiveProfiles("test")
@Transactional
class DeliveryJobsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void refreshAnalyzesEveryProjectAndSkipsJiraWhenItIsNotConfigured() throws Exception {
        Project project = projectRepository.save(new Project("Nightly", "Nightly", null));
        taskRepository.save(new Task(project, "N-1", "Late work", "Core", "Ada", Priority.HIGH,
                TaskStatus.IN_PROGRESS, null, java.time.LocalDate.of(2020, 1, 1), null, null));

        mockMvc.perform(post("/api/jobs/refresh/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true))
                .andExpect(jsonPath("$.manual").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Jira not configured")));

        assertThat(projectRepository.findById(project.getId()).orElseThrow().getLastAnalysisAt()).isNotNull();
        mockMvc.perform(get("/api/jobs"))
                .andExpect(jsonPath("$[0].name").value("refresh"))
                .andExpect(jsonPath("$[0].scheduled").value(false))
                .andExpect(jsonPath("$[0].lastRun.succeeded").value(true));
    }

    @Test
    void weeklyReportJobSavesASnapshotThatCanBeReopened() throws Exception {
        projectRepository.save(new Project("Snapshot", "Snapshot", null));

        mockMvc.perform(post("/api/jobs/weekly-report/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true));

        String list = mockMvc.perform(get("/api/reports/weekly/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trigger").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        String id = new ObjectMapper().readTree(list).path(0).path("id").asText();

        mockMvc.perform(get("/api/reports/weekly/snapshots/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.projects[0].projectName").value("Snapshot"))
                .andExpect(jsonPath("$.activity").exists());
    }

    @Test
    void unknownJobsAndSnapshotsAreNotFound() throws Exception {
        mockMvc.perform(post("/api/jobs/nope/run")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/weekly/snapshots/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportsTheNextRunFromTheCronInTheConfiguredZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        DeliveryJobs jobs = new DeliveryJobs(null, null, null, null, clock, true, "0 0 6 * * *", "0 0 7 * * MON");

        // 2026-10-19 15:30 in Kolkata: today's 06:00 has passed, so tomorrow 06:00 IST; next Monday 07:00 IST.
        assertThat(jobs.statuses()).extracting(DeliveryJobs.JobStatus::nextRunAt).containsExactly(
                Instant.parse("2026-10-20T00:30:00Z"), Instant.parse("2026-10-26T01:30:00Z"));
    }
}
