package com.deliverypulse.upload;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.dependency.DependencyRepository;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.task.TaskRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UploadControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    @Autowired
    private ImportRunRepository importRunRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private com.deliverypulse.task.TaskStatusChangeRepository statusChangeRepository;

    @Test
    void importsFourProjectsAndTwentyFiveTasksAndRejectsRepeatUpload() throws Exception {
        MockMultipartFile workbook = new MockMultipartFile("file", "portfolio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ImportTestFiles.portfolioWorkbook());

        mockMvc.perform(multipart("/api/uploads/excel").file(workbook))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectCount").value(4))
                .andExpect(jsonPath("$.importedTaskCount").value(25))
                .andExpect(jsonPath("$.skippedWorksheets[0]").value("Read Me"))
                .andExpect(jsonPath("$.errors").isEmpty());

        org.assertj.core.api.Assertions.assertThat(projectRepository.count()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(taskRepository.count()).isEqualTo(25);
        org.assertj.core.api.Assertions.assertThat(dependencyRepository.count()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(projectTaskCount("ECommerce")).isEqualTo(8);
        org.assertj.core.api.Assertions.assertThat(projectTaskCount("CRM")).isEqualTo(6);
        org.assertj.core.api.Assertions.assertThat(projectTaskCount("MobileApp")).isEqualTo(6);
        org.assertj.core.api.Assertions.assertThat(projectTaskCount("DataPlatform")).isEqualTo(5);

        mockMvc.perform(multipart("/api/uploads/excel").file(workbook))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PROJECT_ALREADY_EXISTS"));
        org.assertj.core.api.Assertions.assertThat(taskRepository.count()).isEqualTo(25);
        org.assertj.core.api.Assertions.assertThat(importRunRepository.findAll())
                .singleElement()
                .satisfies(run -> org.assertj.core.api.Assertions.assertThat(run.getStartedAt())
                        .isBeforeOrEqualTo(run.getCompletedAt()));
    }

    @Test
    void reportsMissingFilePartAsProblemDetail() throws Exception {
        mockMvc.perform(multipart("/api/uploads/excel").param("other", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void importsSingleProjectCsvAndReturnsRowErrorsWithoutPersistingInvalidRows() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "Orders.csv", "text/csv",
                ImportTestFiles.csv("A-1,Payment review,Core,Ada,High,In Progress,2026-10-01,2026-10-10,,\n"));
        mockMvc.perform(multipart("/api/uploads/csv").file(csv))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.importedTaskCount").value(1));

        MockMultipartFile invalid = new MockMultipartFile("file", "BadProject.csv", "text/csv",
                ImportTestFiles.csv("B-1,Invalid task,Core,Ada,Impossible,In Progress,,,,\n"));
        mockMvc.perform(multipart("/api/uploads/csv").file(invalid))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PRIORITY"));

        org.assertj.core.api.Assertions.assertThat(projectRepository.existsByNameIgnoreCase("BadProject")).isFalse();
    }

    @Test
    void persistsRepeatedDependencyReferencesWithoutViolatingTheUniquePair() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "Repeats.csv", "text/csv",
                ImportTestFiles.csv("A-1,First,Core,Ada,High,Done,,,,\n"
                        + "B-1,Second,Core,Ada,High,In Progress,,,A-1; a-1,\n"));

        mockMvc.perform(multipart("/api/uploads/csv").file(csv)).andExpect(status().isCreated());
        // The test transaction never commits, so flush to hit the database constraints for real.
        entityManager.flush();
        org.assertj.core.api.Assertions.assertThat(dependencyRepository.count()).isEqualTo(1);
    }

    @Test
    void updateReimportChangesTasksInPlaceRecordsHistoryAndFeedsRecurringBlockerAndWeeklyActivity() throws Exception {
        String header = "Task ID,Task Name,Priority,Status,Type,Milestone,Blocker / Dependency\n";
        upload("Sync.csv", header + "S-1,Payments,High,Blocked,,Beta,\nS-2,Login bug,High,In Progress,Bug,Beta,\n"
                + "S-3,Docs,Low,Not Started,,,S-1\n", false).andExpect(status().isCreated());
        upload("Sync.csv", header + "S-1,Payments,High,Blocked,,Beta,\n", false)
                .andExpect(status().isConflict());

        upload("Sync.csv", header + "S-1,Payments,High,In Progress,,Beta,\nS-2,Login bug,High,Done,Bug,Beta,\n"
                + "S-4,New work,Medium,Not Started,,,S-1\n", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedTaskCount").value(3))
                .andExpect(jsonPath("$.warnings[0].code").value("TASK_NOT_IN_UPLOAD"));
        upload("Sync.csv", header + "S-1,Payments,High,Blocked,,Beta,\n", true).andExpect(status().isCreated());
        entityManager.flush();

        var project = projectRepository.findByNameIgnoreCase("Sync").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(taskRepository.findAllByProjectId(project.getId()))
                .extracting(com.deliverypulse.task.Task::getTaskKey).containsExactlyInAnyOrder("S-1", "S-2", "S-3", "S-4");
        // S-3's dependency is gone (not in the last files); S-4 -> S-1 was replaced by the final upload too.
        org.assertj.core.api.Assertions.assertThat(dependencyRepository.findAllByDependentTaskProjectId(project.getId()))
                .isEmpty();
        // 4 first appearances + S-1 Blocked->In Progress->Blocked + S-2 In Progress->Done.
        org.assertj.core.api.Assertions.assertThat(statusChangeRepository.findAllByProjectId(project.getId())).hasSize(7);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/projects/" + project.getId() + "/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risks[?(@.category == 'RECURRING_BLOCKER')].supportingTaskIds[0]").value("S-1"))
                .andExpect(jsonPath("$.openDefectCount").value(0))
                .andExpect(jsonPath("$.milestones[0].name").value("Beta"))
                .andExpect(jsonPath("$.milestones[0].status").value("NO_DATE"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activity.tasksAdded").value(4))
                .andExpect(jsonPath("$.activity.tasksBlocked").value(2))
                .andExpect(jsonPath("$.activity.tasksUnblocked").value(1))
                .andExpect(jsonPath("$.activity.defectsOpened").value(1))
                .andExpect(jsonPath("$.activity.defectsClosed").value(1))
                .andExpect(jsonPath("$.activityByProject['" + project.getId() + "'].tasksCompleted").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions upload(String name, String body, boolean update)
            throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", name, "text/csv",
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/uploads/csv").file(csv).param("update", String.valueOf(update)));
    }

        private long projectTaskCount(String projectName) {
                var project = projectRepository.findByNameIgnoreCase(projectName).orElseThrow();
                return taskRepository.findAllByProjectId(project.getId()).size();
        }

        @Test
        void rejectsCaseInsensitiveDuplicateProjectName() throws Exception {
                projectRepository.saveAndFlush(new com.deliverypulse.project.Project("ExistingProject", "ExistingProject", null));
                MockMultipartFile csv = new MockMultipartFile("file", "existingproject.csv", "text/csv",
                                ImportTestFiles.csv("E-1,Task,Core,Ada,High,Done,,,,\n"));

                mockMvc.perform(multipart("/api/uploads/csv").file(csv))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.errors[0].code").value("PROJECT_ALREADY_EXISTS"));
                org.assertj.core.api.Assertions.assertThat(projectRepository.count()).isEqualTo(1);
                org.assertj.core.api.Assertions.assertThat(taskRepository.count()).isZero();
        }

    @Test
    void rejectsUnsupportedAndMalformedUploads() throws Exception {
        MockMultipartFile wrongExtension = new MockMultipartFile("file", "data.txt", "text/plain", "x".getBytes());
        mockMvc.perform(multipart("/api/uploads/excel").file(wrongExtension))
                .andExpect(status().isUnsupportedMediaType());

        MockMultipartFile malformed = new MockMultipartFile("file", "broken.xlsx", "application/octet-stream",
                "not an Excel workbook".getBytes());
        mockMvc.perform(multipart("/api/uploads/excel").file(malformed))
                .andExpect(status().isBadRequest());
    }

        @Test
        void rejectsFilesLargerThanTenMegabytes() throws Exception {
                MockMultipartFile oversized = new MockMultipartFile("file", "large.csv", "text/csv",
                                new byte[10 * 1024 * 1024 + 1]);
                mockMvc.perform(multipart("/api/uploads/csv").file(oversized))
                                .andExpect(status().isPayloadTooLarge());
        }
}