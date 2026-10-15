package com.deliverypulse.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;
import com.deliverypulse.task.Task;
import com.deliverypulse.task.TaskRepository;

@DataJpaTest
@ActiveProfiles("test")
class ProjectRepositoryTests {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void savesProjectAndItsTaskWithProjectScopedTaskKey() {
        Project project = projectRepository.saveAndFlush(new Project("Checkout Refresh", "Checkout", null));
        Task task = taskRepository.saveAndFlush(new Task(
                project, "PAY-17", "Review payment flow", Priority.HIGH, TaskStatus.IN_PROGRESS));

        assertThat(projectRepository.findById(project.getId()))
                .get()
                .extracting(Project::getName)
                .isEqualTo("Checkout Refresh");
        assertThat(taskRepository.findAllByProjectId(project.getId()))
                .extracting(Task::getTaskKey)
                .containsExactly(task.getTaskKey());
    }
}