package com.deliverypulse.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.insights.llm.LlmClient;
import com.deliverypulse.insights.llm.LlmPrompt;
import com.deliverypulse.insights.llm.LlmResult;
import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.risk.Risk;
import com.deliverypulse.risk.RiskAnalysisService;
import com.deliverypulse.risk.RiskRepository;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

class AiInsightsControllerTests {

    abstract static class Fixture {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ProjectRepository projectRepository;
        @Autowired
        TaskRepository taskRepository;
        @Autowired
        RiskRepository riskRepository;
        @Autowired
        RiskAnalysisService riskAnalysisService;

        UUID analyzedProject() {
            Project project = projectRepository.save(new Project("Atlas", "Atlas", null));
            taskRepository.saveAll(List.of(
                    new Task(project, "AT-1", "Discovery", "Core", "Mira", Priority.MEDIUM, TaskStatus.DONE,
                            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), null, null),
                    new Task(project, "AT-2", "Payment API", "Core", "Mira", Priority.CRITICAL,
                            TaskStatus.IN_PROGRESS, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 12), null,
                            null),
                    new Task(project, "AT-3", "Vendor onboarding", "Ops", "Leo", Priority.HIGH,
                            TaskStatus.BLOCKED, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 20),
                            InsightTestData.INJECTION, null),
                    new Task(project, "AT-4", "Go-live", "Ops", "Leo", Priority.HIGH, TaskStatus.NOT_STARTED,
                            LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 25), null, null)));
            riskAnalysisService.analyzeProject(project.getId(), LocalDate.of(2026, 10, 15));
            return project.getId();
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Transactional
    class WithDefaultConfiguration extends Fixture {

        @Test
        void reportsAiAsDisabledByDefault() throws Exception {
            mockMvc.perform(get("/api/ai/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.status").value("DISABLED"))
                    .andExpect(jsonPath("$.externalDataSharingAuthorized").value(false));
        }

        @Test
        void servesTheDeterministicFallbackWithoutAnApiKey() throws Exception {
            UUID projectId = analyzedProject();

            mockMvc.perform(post("/api/projects/{id}/insights", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.source").value("DETERMINISTIC_FALLBACK"))
                    .andExpect(jsonPath("$.status").value("DISABLED"))
                    .andExpect(jsonPath("$.executiveSummary[0].basis").value("FACT"))
                    .andExpect(jsonPath("$.riskExplanations[0].severity").value("CRITICAL"))
                    .andExpect(jsonPath("$.weeklyStatus.progress[0].supportingTaskIds[0]").value("AT-1"));
        }

        @Test
        void returnsNotFoundForUnknownProjects() throws Exception {
            mockMvc.perform(post("/api/projects/{id}/insights", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Project not found"));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Transactional
    @TestPropertySource(properties = {
            "app.ai.enabled=true",
            "app.ai.external-data-sharing-authorized=true" })
    class WithAiEnabledAndMockedProvider extends Fixture {

        @MockitoBean
        LlmClient llmClient;

        @Test
        void returnsValidatedAiInsightsAndLeavesStoredDataUnchanged() throws Exception {
            when(llmClient.isConfigured()).thenReturn(true);
            when(llmClient.providerName()).thenReturn("anthropic");
            when(llmClient.generate(any(LlmPrompt.class), eq(AiInsightDraft.class)))
                    .thenReturn(new LlmResult<>(InsightTestData.validDraft(), "claude-opus-5-5", 900, 300));
            UUID projectId = analyzedProject();
            Map<UUID, String> risksBefore = riskSnapshot(projectId);
            Map<String, TaskStatus> tasksBefore = taskSnapshot(projectId);

            mockMvc.perform(get("/api/ai/status"))
                    .andExpect(jsonPath("$.available").value(true))
                    .andExpect(jsonPath("$.status").value("GENERATED"));
            mockMvc.perform(post("/api/projects/{id}/insights", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.source").value("AI"))
                    .andExpect(jsonPath("$.status").value("GENERATED"))
                    .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                    .andExpect(jsonPath("$.executiveSummary[0].supportingTaskIds[0]").value("AT-2"))
                    .andExpect(jsonPath("$.executiveSummary[1].basis").value("ASSUMPTION"))
                    .andExpect(jsonPath("$.riskExplanations[0].severity").value("CRITICAL"))
                    .andExpect(jsonPath("$.disclaimer").isNotEmpty());

            assertThat(riskSnapshot(projectId)).isEqualTo(risksBefore);
            assertThat(taskSnapshot(projectId)).isEqualTo(tasksBefore);
        }

        @Test
        void skipsTheProviderUntilTheProjectHasBeenAnalyzed() throws Exception {
            when(llmClient.isConfigured()).thenReturn(true);
            Project project = projectRepository.save(new Project("Fresh", "Fresh", null));

            mockMvc.perform(post("/api/projects/{id}/insights", project.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ANALYSIS_REQUIRED"));
            verify(llmClient, never()).generate(any(), any());
        }

        private Map<UUID, String> riskSnapshot(UUID projectId) {
            return riskRepository.findAllByProjectId(projectId).stream().collect(Collectors.toMap(Risk::getId,
                    risk -> risk.getSeverity() + "/" + risk.getCertainty() + "/" + risk.getStatus()));
        }

        private Map<String, TaskStatus> taskSnapshot(UUID projectId) {
            return taskRepository.findAllByProjectId(projectId).stream()
                    .collect(Collectors.toMap(Task::getTaskKey, Task::getStatus));
        }
    }
}
