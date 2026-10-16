package com.deliverypulse.insights;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.deliverypulse.insights.AiInsightDraft.DraftAction;
import com.deliverypulse.insights.AiInsightDraft.DraftRiskExplanation;
import com.deliverypulse.insights.AiInsightDraft.DraftStatement;
import com.deliverypulse.insights.AiInsightDraft.DraftWeeklyStatus;
import com.deliverypulse.insights.InsightContextBuilder.InsightReferences;
import com.deliverypulse.insights.ProjectInsightsResponse.ActionInsight;
import com.deliverypulse.insights.ProjectInsightsResponse.InsightStatement;
import com.deliverypulse.insights.ProjectInsightsResponse.RiskInsight;
import com.deliverypulse.insights.ProjectInsightsResponse.WeeklyStatusInsight;
import com.deliverypulse.risk.RiskResponse;

/**
 * Checks a model draft against the project data it was given. Items that cite unknown task IDs or
 * risk refs are removed; unsupported facts are downgraded to assumptions; risk metadata always comes
 * from the deterministic engine.
 */
@Component
public class InsightResponseValidator {

    static final int MAX_TEXT_LENGTH = 1200;
    static final int MAX_SUMMARY_ITEMS = 6;
    static final int MAX_ACTIONS = 8;
    static final int MAX_SECTION_ITEMS = 8;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");

    public ValidatedInsights validate(AiInsightDraft draft, InsightReferences context, int maxExplainedRisks) {
        if (draft == null) {
            throw new InvalidInsightResponseException("The model returned no content.");
        }
        List<String> warnings = new ArrayList<>();
        List<InsightStatement> summary = statements(draft.executiveSummary(), context, "Executive summary",
                MAX_SUMMARY_ITEMS, warnings);
        if (summary.isEmpty()) {
            throw new InvalidInsightResponseException("The model returned no valid executive summary.");
        }
        List<RiskInsight> risks = riskExplanations(draft.riskExplanations(), context, maxExplainedRisks, warnings);
        List<ActionInsight> actions = actions(draft.recommendedActions(), context, warnings);
        DraftWeeklyStatus weekly = draft.weeklyStatus();
        WeeklyStatusInsight weeklyStatus = weekly == null
                ? new WeeklyStatusInsight(List.of(), List.of(), List.of(), List.of())
                : new WeeklyStatusInsight(
                        statements(weekly.summary(), context, "Weekly summary", MAX_SECTION_ITEMS, warnings),
                        statements(weekly.progress(), context, "Weekly progress", MAX_SECTION_ITEMS, warnings),
                        statements(weekly.risksAndIssues(), context, "Weekly risks", MAX_SECTION_ITEMS, warnings),
                        statements(weekly.nextSteps(), context, "Weekly next steps", MAX_SECTION_ITEMS, warnings));
        if (weekly == null) {
            warnings.add("The model returned no weekly status draft.");
        }
        return new ValidatedInsights(summary, risks, actions, weeklyStatus, List.copyOf(warnings));
    }

    private List<InsightStatement> statements(List<DraftStatement> drafts, InsightReferences context,
            String section, int maxItems, List<String> warnings) {
        List<InsightStatement> result = new ArrayList<>();
        if (drafts == null) {
            return result;
        }
        for (DraftStatement draft : drafts) {
            if (draft == null) {
                continue;
            }
            if (result.size() == maxItems) {
                warnings.add(section + ": extra items beyond " + maxItems + " were omitted.");
                break;
            }
            statement(draft.text(), draft.basis(), draft.supportingTaskIds(), context, section, warnings)
                    .ifPresent(result::add);
        }
        return result;
    }

    private Optional<InsightStatement> statement(String rawText, StatementBasis rawBasis, List<String> rawTaskIds,
            InsightReferences context, String section, List<String> warnings) {
        Optional<String> text = cleanText(rawText, section, warnings);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Optional<List<String>> taskIds = taskIds(rawTaskIds, context, section, warnings);
        if (taskIds.isEmpty()) {
            return Optional.empty();
        }
        StatementBasis basis = rawBasis == null ? StatementBasis.ASSUMPTION : rawBasis;
        if (basis == StatementBasis.FACT && taskIds.get().isEmpty()) {
            warnings.add(section + ": a statement labelled FACT cited no task IDs and was relabelled ASSUMPTION.");
            basis = StatementBasis.ASSUMPTION;
        }
        return Optional.of(new InsightStatement(text.get(), basis, taskIds.get()));
    }

    private List<RiskInsight> riskExplanations(List<DraftRiskExplanation> drafts, InsightReferences context,
            int maxExplainedRisks, List<String> warnings) {
        List<RiskInsight> result = new ArrayList<>();
        if (drafts == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (DraftRiskExplanation draft : drafts) {
            if (draft == null) {
                continue;
            }
            String ref = draft.riskRef() == null ? "" : draft.riskRef().trim().toUpperCase(Locale.ROOT);
            RiskResponse risk = context.risksByRef().get(ref);
            if (risk == null) {
                warnings.add("Risk explanation for unknown risk ref '" + truncate(draft.riskRef(), 20)
                        + "' was removed.");
                continue;
            }
            if (!seen.add(ref)) {
                continue;
            }
            if (result.size() == maxExplainedRisks) {
                warnings.add("Risk explanations beyond the top " + maxExplainedRisks + " were omitted.");
                break;
            }
            List<InsightStatement> explanation = statements(draft.explanation(), context,
                    "Risk " + ref, MAX_SECTION_ITEMS, warnings);
            if (explanation.isEmpty()) {
                warnings.add("Risk " + ref + ": explanation had no valid statements and was removed.");
                continue;
            }
            result.add(new RiskInsight(risk.id(), risk.title(), risk.severity(), risk.category(),
                    risk.certainty(), risk.supportingTaskIds(), explanation));
        }
        return result;
    }

    private List<ActionInsight> actions(List<DraftAction> drafts, InsightReferences context,
            List<String> warnings) {
        List<ActionInsight> result = new ArrayList<>();
        if (drafts == null) {
            return result;
        }
        for (DraftAction draft : drafts) {
            if (draft == null) {
                continue;
            }
            if (result.size() == MAX_ACTIONS) {
                warnings.add("Recommended actions beyond " + MAX_ACTIONS + " were omitted.");
                break;
            }
            Optional<InsightStatement> statement = statement(draft.action(), draft.basis(),
                    draft.supportingTaskIds(), context, "Recommended action", warnings);
            if (statement.isEmpty()) {
                continue;
            }
            List<UUID> riskIds = new ArrayList<>();
            boolean unknownRisk = false;
            for (String rawRef : draft.relatedRiskRefs() == null ? List.<String>of() : draft.relatedRiskRefs()) {
                RiskResponse risk = rawRef == null ? null
                        : context.risksByRef().get(rawRef.trim().toUpperCase(Locale.ROOT));
                if (risk == null) {
                    unknownRisk = true;
                } else if (!riskIds.contains(risk.id())) {
                    riskIds.add(risk.id());
                }
            }
            if (unknownRisk) {
                warnings.add("Recommended action: an action citing an unknown risk ref was removed.");
                continue;
            }
            result.add(new ActionInsight(statement.get().text(), statement.get().basis(),
                    statement.get().supportingTaskIds(), List.copyOf(riskIds)));
        }
        return result;
    }

    /** Maps cited IDs to their canonical spelling; empty Optional means the item cites an unknown task. */
    private Optional<List<String>> taskIds(List<String> rawTaskIds, InsightReferences context,
            String section, List<String> warnings) {
        Set<String> canonical = new LinkedHashSet<>();
        for (String rawId : rawTaskIds == null ? List.<String>of() : rawTaskIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String known = context.taskIdsByNormalized().get(InsightContextBuilder.normalize(rawId));
            if (known == null) {
                warnings.add(section + ": a statement citing unknown task ID '" + truncate(rawId, 40)
                        + "' was removed.");
                return Optional.empty();
            }
            canonical.add(known);
        }
        return Optional.of(List.copyOf(canonical));
    }

    private Optional<String> cleanText(String rawText, String section, List<String> warnings) {
        if (rawText == null) {
            return Optional.empty();
        }
        String text = CONTROL_CHARACTERS.matcher(rawText).replaceAll("").strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            warnings.add(section + ": an overlong statement was removed.");
            return Optional.empty();
        }
        return Optional.of(text);
    }

    private static String truncate(String value, int length) {
        if (value == null) {
            return "";
        }
        return value.length() <= length ? value : value.substring(0, length) + "…";
    }

    public record ValidatedInsights(
            List<InsightStatement> executiveSummary,
            List<RiskInsight> riskExplanations,
            List<ActionInsight> recommendedActions,
            WeeklyStatusInsight weeklyStatus,
            List<String> warnings) {
    }

    public static class InvalidInsightResponseException extends RuntimeException {

        public InvalidInsightResponseException(String message) {
            super(message);
        }
    }
}
