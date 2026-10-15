package com.deliverypulse.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProjectPortfolioApiTests.FixedTimeConfiguration.class)
@Transactional
class ProjectPortfolioApiTests {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 15);
    private static final Instant GENERATED_AT = Instant.parse("2026-10-15T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RiskAnalysisService riskAnalysisService;

    private Project alpha;
    private Project beta;

    @BeforeEach
    void setUp() {
        alpha = projectRepository.saveAndFlush(new Project("Alpha", "Alpha", "Primary sample project"));
        beta = projectRepository.saveAndFlush(new Project("Beta", "Beta", "Secondary sample project"));

        taskRepository.saveAndFlush(task(alpha, "A-1", "Completed work", Priority.LOW,
                TaskStatus.DONE, LocalDate.of(2026, 10, 1), "Mira"));
        taskRepository.saveAndFlush(task(alpha, "A-2", "Late implementation", Priority.HIGH,
                TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 14), "Mira"));
        taskRepository.saveAndFlush(task(alpha, "A-3", "Critical blocker", Priority.CRITICAL,
                TaskStatus.BLOCKED, LocalDate.of(2026, 10, 20), "Noah"));
        taskRepository.saveAndFlush(task(alpha, "A-4", "Due today", Priority.MEDIUM,
                TaskStatus.NOT_STARTED, AS_OF, "Noah"));
        taskRepository.saveAndFlush(task(beta, "B-1", "Future work", Priority.MEDIUM,
                TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 20), "Mira"));
    }

    @Test
    void listsProjectDetailsAndReturnsTaskFiltersAndPages() throws Exception {
        analyzeBothProjects();

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[0].taskCount").value(4))
                .andExpect(jsonPath("$[0].completedTaskCount").value(1))
                .andExpect(jsonPath("$[0].incompleteTaskCount").value(3))
                .andExpect(jsonPath("$[0].overdueTaskCount").value(1))
                .andExpect(jsonPath("$[0].blockedTaskCount").value(1))
                .andExpect(jsonPath("$[0].risksBySeverity.HIGH").value(2))
                .andExpect(jsonPath("$[0].risksBySeverity.CRITICAL").value(1))
                .andExpect(jsonPath("$[0].analysisDate").value("2026-10-15"))
                .andExpect(jsonPath("$[0].analysisTimestamp").value(GENERATED_AT.toString()));

        mockMvc.perform(get("/api/projects/{id}", alpha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Primary sample project"))
                .andExpect(jsonPath("$.taskCount").value(4));

        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId())
                        .param("status", "BLOCKED")
                        .param("priority", "CRITICAL")
                        .param("assignee", "noah")
                        .param("analysisDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].taskId").value("A-3"));

        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId())
                        .param("overdue", "true").param("analysisDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].taskId").value("A-2"))
                .andExpect(jsonPath("$.content[0].overdue").value(true));

        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId())
                        .param("overdue", "false").param("size", "2").param("page", "0")
                        .param("analysisDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId())
                        .param("overdue", "false").param("sortBy", "TASK_NAME")
                        .param("sortDirection", "DESC").param("analysisDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBy").value("TASK_NAME"))
                .andExpect(jsonPath("$.sortDirection").value("DESC"))
                .andExpect(jsonPath("$.content[0].taskId").value("A-4"));

        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId())
                        .param("status", "DONE").param("overdue", "true")
                        .param("analysisDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void portfolioAndWeeklyReportAggregateActualProjectData() throws Exception {
        analyzeBothProjects();

        mockMvc.perform(get("/api/dashboard/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(2))
                .andExpect(jsonPath("$.taskCount").value(5))
                .andExpect(jsonPath("$.completedTaskCount").value(1))
                .andExpect(jsonPath("$.incompleteTaskCount").value(4))
                .andExpect(jsonPath("$.overdueTaskCount").value(1))
                .andExpect(jsonPath("$.blockedTaskCount").value(1))
                .andExpect(jsonPath("$.activeRiskCount").value(3))
                .andExpect(jsonPath("$.risksBySeverity.HIGH").value(2))
                .andExpect(jsonPath("$.risksBySeverity.CRITICAL").value(1));

        mockMvc.perform(get("/api/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStartDate").value("2026-10-09"))
                .andExpect(jsonPath("$.weekEndDate").value("2026-10-15"))
                .andExpect(jsonPath("$.analysisDate").value("2026-10-15"))
                .andExpect(jsonPath("$.analysisTimestamp").value(GENERATED_AT.toString()))
                .andExpect(jsonPath("$.projectCount").value(2))
                .andExpect(jsonPath("$.taskCount").value(5))
                .andExpect(jsonPath("$.activeRiskCount").value(3))
                .andExpect(jsonPath("$.projects.length()").value(2));
    }

    @Test
    void taskOverdueFlagsDefaultToTheProjectsLastAnalysisDate() throws Exception {
        LocalDate earlierAnalysis = LocalDate.of(2026, 10, 10);
        riskAnalysisService.analyzeProject(alpha.getId(), earlierAnalysis);

        mockMvc.perform(get("/api/projects/{id}", alpha.getId()))
                .andExpect(jsonPath("$.overdueTaskCount").value(0));
        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId()).param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisDate").value(earlierAnalysis.toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void reportsMissingProjectsAndInvalidQueryParametersAsBadRequests() throws Exception {
        UUID missingId = UUID.randomUUID();
        mockMvc.perform(get("/api/projects/{id}", missingId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Project not found"));
        mockMvc.perform(get("/api/projects/{id}/tasks", missingId)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId()).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId()).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId()).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/projects/{id}/tasks", alpha.getId()).param("overdue", "sometimes"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsLocalFrontendCorsPreflightOnlyForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/dashboard/portfolio")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mockMvc.perform(options("/api/dashboard/portfolio")
                        .header("Origin", "http://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private void analyzeBothProjects() {
        riskAnalysisService.analyzeProject(alpha.getId(), AS_OF);
        riskAnalysisService.analyzeProject(beta.getId(), AS_OF);
    }

    private Task task(Project project, String taskId, String taskName, Priority priority,
            TaskStatus status, LocalDate dueDate, String assignee) {
        return new Task(project, taskId, taskName, "Core", assignee, priority, status,
                dueDate.minusDays(3), dueDate, null, null);
    }

    @TestConfiguration
    static class FixedTimeConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(GENERATED_AT, ZoneOffset.UTC);
        }
    }
}