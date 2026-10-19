package com.deliverypulse.risk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.dependency.Dependency;
import com.deliverypulse.dependency.DependencyRepository;
import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
@ActiveProfiles("test")
@Transactional
class RiskAnalysisControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    @Autowired
    private RiskRepository riskRepository;

    @Test
    void analyzesProjectAndAggregatesRefreshedRisksAcrossPortfolio() throws Exception {
        Project project = projectRepository.saveAndFlush(new Project("Risk API Project", "Risk API Project", null));
        taskRepository.saveAndFlush(new Task(project, "API-1", "Overdue work", "Core", "Owner",
                Priority.HIGH, TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 14), null, null));
        Task prerequisite = taskRepository.saveAndFlush(new Task(project, "API-2", "Prerequisite", "Core", "Owner",
                Priority.MEDIUM, TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 15), null, null));
        Task dependent = taskRepository.saveAndFlush(new Task(project, "API-3", "Dependent work", "Core", "Owner",
                Priority.MEDIUM, TaskStatus.NOT_STARTED, LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 20), null, null));
        dependencyRepository.saveAndFlush(new Dependency(dependent, prerequisite, "API-2"));

        mockMvc.perform(post("/api/projects/{id}/analyze", project.getId()).param("analysisDate", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisDate").value("2026-10-15"))
                .andExpect(jsonPath("$.taskCount").value(3))
                .andExpect(jsonPath("$.activeRiskCount").value(2))
                .andExpect(jsonPath("$.risksBySeverity.HIGH").value(1))
                .andExpect(jsonPath("$.risksBySeverity.MEDIUM").value(1))
                .andExpect(jsonPath("$.risks[0].category").value("OVERDUE_TASK"))
                .andExpect(jsonPath("$.risks[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.risks[1].category").value("DEPENDENCY_RISK"))
                .andExpect(jsonPath("$.risks[1].certainty").value("POTENTIAL"))
                .andExpect(jsonPath("$.risks[1].supportingTaskIds.length()").value(2));

        mockMvc.perform(post("/api/projects/{id}/analyze", project.getId()).param("analysisDate", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRiskCount").value(2));
        org.assertj.core.api.Assertions.assertThat(riskRepository.findAllByProjectId(project.getId())).hasSize(2);

        mockMvc.perform(get("/api/projects/{id}/risks", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRiskCount").value(2));
        mockMvc.perform(get("/api/dashboard/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.taskCount").value(3))
                .andExpect(jsonPath("$.activeRiskCount").value(2));
    }

    @Test
    void returnsNotFoundForUnknownProject() throws Exception {
        mockMvc.perform(post("/api/projects/{id}/analyze", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

}