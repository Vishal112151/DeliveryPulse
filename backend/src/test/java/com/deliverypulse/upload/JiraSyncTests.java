package com.deliverypulse.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.task.TaskStatusChangeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "app.jira.base-url=https://example.atlassian.net",
        "app.jira.email=me@example.com",
        "app.jira.api-token=secret-token",
        "app.jira.project-keys=DPDEMO"})
class JiraSyncTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskStatusChangeRepository statusChangeRepository;

    @MockitoBean
    private JiraClient jiraClient;

    @Test
    void statusNeverReturnsCredentials() throws Exception {
        mockMvc.perform(get("/api/integrations/jira/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.projectKeys[0]").value("DPDEMO"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret-token"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("me@example.com"))));
    }

    @Test
    void repeatedSyncsUpdateTheProjectAndRecordStatusChanges() throws Exception {
        var issues = JiraTestData.issues();
        when(jiraClient.fetchIssues()).thenReturn(issues);
        mockMvc.perform(post("/api/integrations/jira/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedProjects[0]").value("DeliveryPulse Demo"))
                .andExpect(jsonPath("$.importedTaskCount").value(5));

        // Next sync: DPDEMO-2 is unblocked (label removed).
        ObjectNode unblocked = issues.get(1).deepCopy();
        ((ObjectNode) unblocked.path("fields")).set("labels", new ObjectMapper().createArrayNode());
        when(jiraClient.fetchIssues()).thenReturn(List.of(issues.get(0), unblocked, issues.get(2), issues.get(3), issues.get(4)));
        mockMvc.perform(post("/api/integrations/jira/sync")).andExpect(status().isOk());

        var project = projectRepository.findByNameIgnoreCase("DeliveryPulse Demo").orElseThrow();
        assertThat(statusChangeRepository.findAllByProjectId(project.getId()))
                .hasSize(6)
                .last().satisfies(change -> {
                    assertThat(change.getTask().getTaskKey()).isEqualTo("DPDEMO-2");
                    assertThat(change.getToStatus()).hasToString("IN_PROGRESS");
                });
    }

    @Test
    void refusesToSyncWhenNotConfigured() {
        JiraSyncService unconfigured = new JiraSyncService(
                new JiraProperties("", null, null, List.of(), null, null, 0), null, null);

        assertThatThrownBy(unconfigured::sync)
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(unconfigured.status().configured()).isFalse();
    }
}
