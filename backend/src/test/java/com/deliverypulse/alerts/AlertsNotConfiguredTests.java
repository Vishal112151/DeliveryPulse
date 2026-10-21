package com.deliverypulse.alerts;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertsNotConfiguredTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void alertsAreOffWithoutAMailServer() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.mailServerConfigured").value(false));
        mockMvc.perform(post("/api/alerts/test")).andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void onlyAdminsCanUseAlerts() throws Exception {
        mockMvc.perform(get("/api/alerts")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/alerts/test")).andExpect(status().isForbidden());
    }
}
