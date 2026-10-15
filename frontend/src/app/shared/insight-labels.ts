import { InsightStatus, ProjectInsightsResponse } from '../core/api.models';

const STATUS_LABELS: Record<InsightStatus, string> = {
  GENERATED: 'AI insights generated',
  DISABLED: 'AI insights are turned off',
  NOT_CONFIGURED: 'No AI provider key configured',
  NOT_AUTHORIZED: 'External AI data sharing not authorized',
  ANALYSIS_REQUIRED: 'Run risk analysis first',
  TIMEOUT: 'AI provider timed out',
  RATE_LIMITED: 'AI provider rate limit reached',
  QUOTA_EXCEEDED: 'AI provider quota exhausted',
  AUTHENTICATION_FAILED: 'AI provider rejected the credentials',
  UNAVAILABLE: 'AI provider unavailable',
  REFUSED: 'AI provider declined the request',
  INVALID_RESPONSE: 'AI response failed validation',
};

export function insightStatusLabel(status: InsightStatus): string {
  return STATUS_LABELS[status] ?? 'AI insights unavailable';
}

export function sourceLabel(insights: Pick<ProjectInsightsResponse, 'source'>): string {
  return insights.source === 'AI' ? 'AI-generated draft' : 'Deterministic summary';
}

/** Statuses where the deterministic summary is shown because something went wrong, not by configuration. */
export function isDegraded(status: InsightStatus): boolean {
  return !['GENERATED', 'DISABLED', 'NOT_CONFIGURED', 'NOT_AUTHORIZED', 'ANALYSIS_REQUIRED'].includes(status);
}
