package com.deliverypulse.alerts;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Email alerts for new Critical/High risks and the weekly report (admin only).")
public class AlertController {

    private final AlertService alerts;

    public AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    @Operation(summary = "Whether email alerts are configured, and how many recipients they go to")
    public AlertService.AlertStatus status() {
        return alerts.status();
    }

    @PostMapping("/test")
    @Operation(summary = "Email every open Critical/High risk now, to check the setup")
    public Map<String, Object> sendTest() {
        try {
            int risks = alerts.sendTest();
            return Map.of("sent", true, "riskCount", risks,
                    "message", "Test email sent to " + alerts.status().recipientCount() + " recipient(s) with "
                            + risks + " open Critical/High risk(s).");
        } catch (AlertService.AlertException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        }
    }
}
