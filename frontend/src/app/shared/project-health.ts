import { Component, computed, input } from '@angular/core';
import { RiskSeverity } from '../core/api.models';

export type HealthLevel = 'OFF_TRACK' | 'AT_RISK' | 'ON_TRACK' | 'NOT_ANALYZED';

export interface ProjectHealth {
  level: HealthLevel;
  label: string;
  reason: string;
}

/**
 * One-glance status from the open findings: any Critical is Off track, any High is At risk, otherwise On track.
 * Same rule as the backend's ProjectHealth (used in alert emails); keep the two in step.
 */
export function projectHealth(risksBySeverity: Partial<Record<RiskSeverity, number>>, analyzed: boolean): ProjectHealth {
  if (!analyzed) {
    return { level: 'NOT_ANALYZED', label: 'Not analyzed', reason: 'Run analysis to get a health status.' };
  }
  const critical = risksBySeverity.CRITICAL ?? 0;
  const high = risksBySeverity.HIGH ?? 0;
  if (critical > 0) {
    return { level: 'OFF_TRACK', label: 'Off track', reason: `${critical} critical risk${critical === 1 ? '' : 's'} open` };
  }
  if (high > 0) {
    return { level: 'AT_RISK', label: 'At risk', reason: `${high} high risk${high === 1 ? '' : 's'} open, no critical` };
  }
  return { level: 'ON_TRACK', label: 'On track', reason: 'No critical or high risks open' };
}

export function healthCounts(projects: { risksBySeverity: Partial<Record<RiskSeverity, number>>; analysisTimestamp: string | null }[]): Record<HealthLevel, number> {
  const counts: Record<HealthLevel, number> = { OFF_TRACK: 0, AT_RISK: 0, ON_TRACK: 0, NOT_ANALYZED: 0 };
  for (const project of projects) {
    counts[projectHealth(project.risksBySeverity, project.analysisTimestamp !== null).level]++;
  }
  return counts;
}

@Component({
  selector: 'app-health-badge',
  standalone: true,
  template: `<span class="health" [class]="'health ' + health().level.toLowerCase()" [title]="health().reason"><span class="dot"></span>{{ health().label }}@if (showReason()) {<span class="reason"> · {{ health().reason }}</span>}</span>`,
  styles: [`
    :host { display:inline-flex; }
    .health { display:inline-flex; align-items:center; gap:5px; padding:3px 8px; border-radius:999px; font-size:9px; font-weight:750; white-space:nowrap; }
    .dot { width:7px; height:7px; border-radius:50%; background:currentColor; }
    .reason { font-weight:500; }
    .off_track { color:#b8432f; background:#fdebe6; }
    .at_risk { color:#a8630f; background:#fdf1e0; }
    .on_track { color:#3f7a33; background:#eaf5e4; }
    .not_analyzed { color:#7d877f; background:#f0f2ef; }
  `],
})
export class HealthBadgeComponent {
  readonly risksBySeverity = input.required<Partial<Record<RiskSeverity, number>>>();
  readonly analyzed = input.required<boolean>();
  readonly showReason = input(false);
  protected readonly health = computed(() => projectHealth(this.risksBySeverity(), this.analyzed()));
}
