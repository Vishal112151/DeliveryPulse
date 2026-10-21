package com.deliverypulse.alerts;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deliverypulse.project.Project;
import com.deliverypulse.report.WeeklyActivity;
import com.deliverypulse.report.WeeklyReportResponse;
import com.deliverypulse.risk.ProjectHealth;
import com.deliverypulse.risk.ProjectRiskSummary;
import com.deliverypulse.risk.Risk;
import com.deliverypulse.risk.RiskRepository;
import com.deliverypulse.shared.domain.RiskSeverity;
import com.deliverypulse.shared.domain.RiskStatus;
import com.deliverypulse.task.Task;

/**
 * Email alerts: Critical/High findings that are new since the last refresh, and the weekly report summary.
 * Plain-text emails to the configured recipients; off unless a mail server and recipients are set.
 */
@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    private static final List<RiskSeverity> ALERT_SEVERITIES = List.of(RiskSeverity.CRITICAL, RiskSeverity.HIGH);
    private static final int MAX_RISKS_PER_EMAIL = 25;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final RiskRepository riskRepository;
    private final Optional<JavaMailSender> mailSender;
    private final String from;
    private final List<String> recipients;
    private final String appUrl;

    public AlertService(RiskRepository riskRepository, Optional<JavaMailSender> mailSender,
            @Value("${app.alerts.from:}") String from,
            @Value("${app.alerts.to:}") String to,
            @Value("${app.alerts.app-url:http://localhost:4200}") String appUrl) {
        this.riskRepository = riskRepository;
        this.mailSender = mailSender;
        this.from = from == null ? "" : from.trim();
        this.recipients = Arrays.stream(to == null ? new String[0] : to.split("[,;]"))
                .map(String::trim).filter(address -> !address.isEmpty()).toList();
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return mailSender.isPresent() && !recipients.isEmpty();
    }

    public AlertStatus status() {
        return new AlertStatus(isConfigured(), mailSender.isPresent(), recipients.size());
    }

    /** Ids of the Critical/High findings open now; pass them to {@link #sendNewRisks} after the next analysis. */
    @Transactional(readOnly = true)
    public Set<UUID> openAlertRiskIds() {
        return openAlertRisks().stream().map(Risk::getId).collect(Collectors.toSet());
    }

    /** Emails the Critical/High findings that are open now but were not in {@code previouslyOpen}. Returns how many. */
    @Transactional(readOnly = true)
    public int sendNewRisks(Set<UUID> previouslyOpen) {
        List<Risk> fresh = openAlertRisks().stream().filter(risk -> !previouslyOpen.contains(risk.getId())).toList();
        if (fresh.isEmpty() || !isConfigured()) {
            return 0;
        }
        String subject = "DeliveryPulse: " + fresh.size() + " new Critical/High risk" + (fresh.size() == 1 ? "" : "s");
        send(subject, riskDigest(fresh, fresh.size() + " Critical or High risk" + (fresh.size() == 1 ? " is" : "s are")
                + " new, or came back, in today's refresh.", "the daily refresh"));
        return fresh.size();
    }

    /** Emails every open Critical/High finding now, so an admin can see what an alert looks like. */
    @Transactional(readOnly = true)
    public int sendTest() {
        requireConfigured();
        List<Risk> open = openAlertRisks();
        String intro = open.isEmpty()
                ? "This is a test email. There are no open Critical or High risks right now."
                : "This is a test email. These are the " + open.size() + " Critical and High risks open right now; "
                        + "the daily refresh sends only the ones that are new.";
        send("DeliveryPulse test alert: " + open.size() + " open Critical/High risk" + (open.size() == 1 ? "" : "s"),
                riskDigest(open, intro, "an admin (test)"));
        return open.size();
    }

    public void sendWeeklyReport(WeeklyReportResponse report) {
        if (!isConfigured()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        body.append("Weekly delivery report, ").append(DAY.format(report.weekStartDate())).append(" to ")
                .append(DAY.format(report.weekEndDate())).append(".\n\n");
        body.append("PORTFOLIO\n");
        body.append("  ").append(report.projectCount()).append(" projects, ").append(report.taskCount())
                .append(" tasks: ").append(report.overdueTaskCount()).append(" overdue, ")
                .append(report.blockedTaskCount()).append(" blocked, ").append(report.openDefectCount())
                .append(" open defects\n");
        body.append("  ").append(report.activeRiskCount()).append(" open risks: ")
                .append(severityCounts(report.risksBySeverity())).append("\n");
        WeeklyActivity activity = report.activity();
        body.append("  This week: ").append(activity.tasksAdded()).append(" added, ").append(activity.tasksCompleted())
                .append(" completed, ").append(activity.tasksBlocked()).append(" newly blocked, ")
                .append(activity.tasksUnblocked()).append(" unblocked, ").append(activity.defectsOpened())
                .append(" defects opened, ").append(activity.defectsClosed()).append(" closed\n\n");
        body.append("PROJECTS (worst first)\n");
        report.projects().stream().sorted(WORST_FIRST).forEach(project -> {
            ProjectHealth health = ProjectHealth.of(project.risksBySeverity(), project.analysisTimestamp() != null);
            body.append("  ").append(marker(health)).append(' ').append(project.projectName()).append(" - ")
                    .append(health.label());
            if (health != ProjectHealth.NOT_ANALYZED) {
                body.append(": ").append(severityCounts(project.risksBySeverity())).append("; ")
                        .append(project.overdueTaskCount()).append(" overdue, ").append(project.blockedTaskCount())
                        .append(" blocked");
            }
            body.append("\n");
        });
        body.append("\nOpen the full report: ").append(appUrl).append("/reports\n");
        send("DeliveryPulse weekly report: " + DAY.format(report.weekStartDate()) + " to "
                + DAY.format(report.weekEndDate()), body.append(footer("the weekly report job")).toString());
    }

    private static final Comparator<ProjectRiskSummary> WORST_FIRST = Comparator
            .comparing((ProjectRiskSummary project) -> -project.risksBySeverity().getOrDefault(RiskSeverity.CRITICAL, 0L))
            .thenComparing(project -> -project.risksBySeverity().getOrDefault(RiskSeverity.HIGH, 0L))
            .thenComparing(ProjectRiskSummary::projectName);

    private List<Risk> openAlertRisks() {
        return riskRepository.findAllByStatusNotAndSeverityIn(RiskStatus.RESOLVED, ALERT_SEVERITIES);
    }

    private String riskDigest(List<Risk> risks, String intro, String sentBy) {
        Map<Project, List<Risk>> byProject = risks.stream()
                .sorted(Comparator.comparing((Risk risk) -> -risk.getSeverity().ordinal())
                        .thenComparing(risk -> risk.getProject().getName()))
                .limit(MAX_RISKS_PER_EMAIL)
                .collect(Collectors.groupingBy(Risk::getProject, LinkedHashMap::new, Collectors.toList()));
        StringBuilder body = new StringBuilder(intro).append("\n");
        byProject.forEach((project, projectRisks) -> {
            ProjectHealth health = ProjectHealth.of(openCounts(project), true);
            body.append("\n").append(marker(health)).append(' ').append(project.getName().toUpperCase())
                    .append(" - ").append(health.label()).append("\n");
            for (Risk risk : projectRisks) {
                body.append("\n  [").append(risk.getSeverity()).append("] ").append(risk.getTitle()).append("\n");
                body.append("    Why: ").append(risk.getExplanation()).append("\n");
                if (risk.getEvidence() != null && !risk.getEvidence().isBlank()) {
                    body.append("    Evidence: ").append(risk.getEvidence()).append("\n");
                }
                if (risk.getRecommendedAction() != null && !risk.getRecommendedAction().isBlank()) {
                    body.append("    Next step: ").append(risk.getRecommendedAction()).append("\n");
                }
                String tasks = risk.getSupportingTasks().stream().map(Task::getTaskKey).sorted()
                        .collect(Collectors.joining(", "));
                if (!tasks.isEmpty()) {
                    body.append("    Tasks: ").append(tasks).append("\n");
                }
            }
            body.append("\n  Open project: ").append(appUrl).append("/projects/").append(project.getId()).append("\n");
        });
        if (risks.size() > MAX_RISKS_PER_EMAIL) {
            body.append("\n...and ").append(risks.size() - MAX_RISKS_PER_EMAIL).append(" more. See ")
                    .append(appUrl).append("/risks\n");
        }
        return body.append(footer(sentBy)).toString();
    }

    private Map<RiskSeverity, Long> openCounts(Project project) {
        Map<RiskSeverity, Long> counts = new EnumMap<>(RiskSeverity.class);
        riskRepository.findAllByProjectId(project.getId()).stream()
                .filter(risk -> risk.getStatus() != RiskStatus.RESOLVED)
                .forEach(risk -> counts.merge(risk.getSeverity(), 1L, Long::sum));
        return counts;
    }

    private static String severityCounts(Map<RiskSeverity, Long> counts) {
        return Stream.of(RiskSeverity.CRITICAL, RiskSeverity.HIGH, RiskSeverity.MEDIUM, RiskSeverity.LOW)
                .map(severity -> counts.getOrDefault(severity, 0L) + " " + severity.name().toLowerCase())
                .collect(Collectors.joining(", "));
    }

    private static String marker(ProjectHealth health) {
        return switch (health) {
            case OFF_TRACK -> "[RED]";
            case AT_RISK -> "[AMBER]";
            case ON_TRACK -> "[GREEN]";
            case NOT_ANALYZED -> "[--]";
        };
    }

    private String footer(String sentBy) {
        return "\n--\nSent by DeliveryPulse (" + sentBy + "). Risk rules are deterministic; every finding cites "
                + "the tasks behind it.\n" + appUrl + "\n";
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new AlertException("Email alerts are not configured. Set MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD "
                    + "and ALERT_EMAIL_TO in .env and restart the backend.");
        }
    }

    private void send(String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(recipients.toArray(String[]::new));
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.orElseThrow().send(message);
            logger.info("Alert email sent: subject=\"{}\", recipients={}", subject, recipients.size());
        } catch (MailException exception) {
            // The cause can echo server replies; log the type only, never credentials.
            logger.warn("Alert email failed: {}", exception.getClass().getSimpleName());
            throw new AlertException("The alert email could not be sent (" + exception.getClass().getSimpleName()
                    + "). Check the MAIL_* settings.", exception);
        }
    }

    public record AlertStatus(boolean configured, boolean mailServerConfigured, int recipientCount) {
    }

    public static class AlertException extends RuntimeException {
        AlertException(String message) {
            super(message);
        }

        AlertException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
