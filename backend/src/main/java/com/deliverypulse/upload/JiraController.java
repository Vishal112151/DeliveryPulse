package com.deliverypulse.upload;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/integrations/jira")
@Tag(name = "Jira", description = "Optional read-only sync from Jira Cloud.")
public class JiraController {

    private final JiraSyncService syncService;

    public JiraController(JiraSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/status")
    @Operation(summary = "Report whether Jira sync is configured (never returns credentials)")
    public JiraSyncService.JiraStatusResponse getStatus() {
        return syncService.status();
    }

    @PostMapping("/sync")
    @Operation(summary = "Import the configured Jira projects",
            description = "Reads every issue of JIRA_PROJECT_KEYS and imports them like an upload with update=true: "
                    + "tasks are matched by issue key and status changes are recorded. Nothing is written to Jira.")
    public ImportSummary sync() {
        return syncService.sync();
    }
}
