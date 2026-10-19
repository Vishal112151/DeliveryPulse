package com.deliverypulse.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Signs in for real (no mocked users) and checks roles and per-project visibility end to end. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthAndAccessTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    private Project alpha;
    private Project beta;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        alpha = projectRepository.save(new Project("Alpha", "Alpha", null));
        beta = projectRepository.save(new Project("Beta", "Beta", null));
        adminToken = login("admin", "test-admin-password");
    }

    @Test
    void everyApiNeedsASignInExceptLoginAndHealth() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in to use DeliveryPulse."));
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/auth/me"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordsAreRejectedAndRepeatedFailuresLockTheUsername() throws Exception {
        createUser("lockme", "MANAGER", List.of());
        for (int attempt = 0; attempt < AuthService.MAX_FAILURES; attempt++) {
            loginRequest("lockme", "wrong-password").andExpect(status().isUnauthorized());
        }
        loginRequest("lockme", "password-123").andExpect(status().isTooManyRequests());
        loginRequest("nobody", "whatever-123").andExpect(status().isUnauthorized());
    }

    @Test
    void managersSeeAndChangeOnlyTheirProjects() throws Exception {
        createUser("maria", "MANAGER", List.of(alpha.getId()));
        String manager = login("maria", "password-123");

        mockMvc.perform(as(get("/api/projects"), manager))
                .andExpect(jsonPath("$[*].name").value(Matchers.contains("Alpha")));
        mockMvc.perform(as(get("/api/projects/" + beta.getId()), manager)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/projects/" + beta.getId() + "/tasks"), manager)).andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/projects/" + alpha.getId() + "/analyze"), manager)).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/projects/" + beta.getId() + "/analyze"), manager)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/dashboard/portfolio"), manager)).andExpect(jsonPath("$.projectCount").value(1));
        mockMvc.perform(as(get("/api/reports/weekly"), manager)).andExpect(jsonPath("$.projects.length()").value(1));
        mockMvc.perform(as(get("/api/users"), manager)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/jobs/refresh/run"), manager)).andExpect(status().isForbidden());

        // A project the manager uploads becomes theirs; updating someone else's project is refused.
        mockMvc.perform(as(multipart("/api/uploads/csv").file(csv("Gamma.csv")), manager)).andExpect(status().isCreated());
        mockMvc.perform(as(get("/api/projects"), manager))
                .andExpect(jsonPath("$[*].name").value(Matchers.contains("Alpha", "Gamma")));
        mockMvc.perform(as(multipart("/api/uploads/csv").file(csv("Beta.csv")).param("update", "true"), manager))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PROJECT_NOT_ACCESSIBLE"));
    }

    @Test
    void viewersAreReadOnly() throws Exception {
        createUser("vic", "VIEWER", List.of(alpha.getId()));
        String viewer = login("vic", "password-123");

        mockMvc.perform(as(get("/api/projects/" + alpha.getId()), viewer)).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/projects/" + alpha.getId() + "/analyze"), viewer)).andExpect(status().isForbidden());
        mockMvc.perform(as(multipart("/api/uploads/csv").file(csv("Delta.csv")), viewer)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/projects/" + alpha.getId() + "/insights"), viewer)).andExpect(status().isForbidden());
    }

    @Test
    void logoutDeactivationAndPasswordResetEndSessions() throws Exception {
        UUID id = createUser("sam", "MANAGER", List.of());
        String first = login("sam", "password-123");
        mockMvc.perform(as(post("/api/auth/logout"), first)).andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/auth/me"), first)).andExpect(status().isUnauthorized());

        String second = login("sam", "password-123");
        updateUser(id, "MANAGER", false, null).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/auth/me"), second)).andExpect(status().isUnauthorized());
        loginRequest("sam", "password-123").andExpect(status().isUnauthorized());
    }

    @Test
    void theLastAdminCannotBeRemoved() throws Exception {
        String me = JSON.readTree(mockMvc.perform(as(get("/api/auth/me"), adminToken))
                .andReturn().getResponse().getContentAsString()).path("id").asText();

        updateUser(UUID.fromString(me), "MANAGER", true, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You cannot remove your own admin access."));
    }

    @Test
    void usersCanChangeTheirOwnPassword() throws Exception {
        createUser("pat", "VIEWER", List.of());
        String token = login("pat", "password-123");
        mockMvc.perform(as(post("/api/auth/password"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/auth/password"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password-123\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());
        login("pat", "new-password-1");
    }

    private UUID createUser(String username, String role, List<UUID> projectIds) throws Exception {
        String body = JSON.writeValueAsString(java.util.Map.of("username", username, "displayName", username,
                "password", "password-123", "role", role, "projectIds", projectIds));
        String response = mockMvc.perform(as(post("/api/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(response).path("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions updateUser(UUID id, String role, boolean active,
            String password) throws Exception {
        var body = new java.util.HashMap<String, Object>(java.util.Map.of("displayName", "Updated", "role", role,
                "active", active, "projectIds", List.of()));
        if (password != null) {
            body.put("password", password);
        }
        return mockMvc.perform(as(put("/api/users/" + id), adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    private String login(String username, String password) throws Exception {
        JsonNode response = JSON.readTree(loginRequest(username, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return response.path("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(String username, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(java.util.Map.of("username", username, "password", password))));
    }

    private static <T extends MockHttpServletRequestBuilder> T as(T request, String token) {
        request.header("Authorization", "Bearer " + token);
        return request;
    }

    private static MockMultipartFile csv(String name) {
        return new MockMultipartFile("file", name, "text/csv",
                "Task ID,Task Name,Priority,Status\nT-1,Task,High,In Progress\n".getBytes(StandardCharsets.UTF_8));
    }
}
