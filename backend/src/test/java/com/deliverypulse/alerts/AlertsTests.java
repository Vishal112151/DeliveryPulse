package com.deliverypulse.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

/** Alerts with a mocked mail server: nothing is really sent. */
@SpringBootTest(properties = "app.alerts.to=pm@example.com, lead@example.com")
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@ActiveProfiles("test")
@Transactional
class AlertsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    private Project projectWithOverdueTask() {
        Project project = projectRepository.save(new Project("Alerted", "Alerted", null));
        taskRepository.save(new Task(project, "AL-1", "Late work", "Core", "Ada", Priority.HIGH,
                TaskStatus.IN_PROGRESS, null, LocalDate.of(2020, 1, 1), null, null));
        return project;
    }

    @Test
    void refreshEmailsNewHighRisksOnlyOnce() throws Exception {
        Project project = projectWithOverdueTask();

        mockMvc.perform(post("/api/jobs/refresh/run"))
                .andExpect(jsonPath("$.succeeded").value(true))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("emailed 1 new risks")));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("pm@example.com", "lead@example.com");
        assertThat(message.getSubject()).isEqualTo("DeliveryPulse: 1 new Critical/High risk");
        assertThat(message.getText()).contains("ALERTED - At risk", "[HIGH]", "Tasks: AL-1",
                "Open project: http://localhost:4200/projects/" + project.getId());

        mockMvc.perform(post("/api/jobs/refresh/run"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("no new Critical/High risks")));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void testEmailListsEveryOpenHighRisk() throws Exception {
        projectWithOverdueTask();
        mockMvc.perform(post("/api/jobs/refresh/run"));

        mockMvc.perform(get("/api/alerts"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.recipientCount").value(2));
        mockMvc.perform(post("/api/alerts/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.riskCount").value(1));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        assertThat(sent.getValue().getSubject()).startsWith("DeliveryPulse test alert");
    }

    @Test
    void weeklyReportJobEmailsTheSummaryWorstProjectFirst() throws Exception {
        projectWithOverdueTask();
        projectRepository.save(new Project("Quiet", "Quiet", null));
        mockMvc.perform(post("/api/jobs/refresh/run"));

        mockMvc.perform(post("/api/jobs/weekly-report/run"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("emailed the summary")));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        List<SimpleMailMessage> messages = sent.getAllValues();
        SimpleMailMessage weekly = messages.get(1);
        assertThat(weekly.getSubject()).startsWith("DeliveryPulse weekly report");
        String text = weekly.getText();
        assertThat(text).contains("[AMBER] Alerted - At risk", "[GREEN] Quiet - On track");
        assertThat(text.indexOf("Alerted")).isLessThan(text.indexOf("Quiet"));
    }
}
