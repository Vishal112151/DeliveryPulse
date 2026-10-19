import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideFileText as FileText, LucideRefreshCw as RefreshCw, LucideTriangleAlert as TriangleAlert } from '@lucide/angular';
import { AiStatusResponse, InsightStatement, ProjectInsightsResponse } from '../core/api.models';
import { AuthService } from '../core/auth.service';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { insightStatusLabel, isDegraded, sourceLabel } from './insight-labels';
import { LucideIconCompatComponent } from './lucide-icon-compat.component';
import { errorMessage, formatDateTime, titleCase } from './view-helpers';

@Component({
  selector: 'app-ai-insights-panel',
  imports: [NgTemplateOutlet, LucideIconCompatComponent],
  template: `
    <section class="surface insights-surface" aria-labelledby="insights-heading">
      <div class="section-heading">
        <div><div class="eyebrow">{{ projectId() ? 'INSIGHTS' : 'PORTFOLIO INSIGHTS' }}</div><h2 id="insights-heading">{{ projectId() ? 'Summary &amp; weekly status' : 'Portfolio briefing' }}</h2></div>
        <div class="heading-actions">
          @if (aiStatus(); as status) {
            <span class="ai-chip" [class.on]="status.available" [title]="status.message">{{ status.available ? 'AI: ' + status.model : 'AI off' }}</span>
          }
          @if (auth.canEdit()) { <button class="button button-secondary" type="button" (click)="generate()" [disabled]="loading()">
            @if (loading()) { <span class="spinner"></span>Generating… } @else { <lucide-icon [img]="RefreshIcon" [size]="15" /> {{ aiStatus()?.available ? (projectId() ? 'Generate AI insights' : 'Generate AI briefing') : (projectId() ? 'Generate summary' : 'Generate briefing') }} }
          </button> }
        </div>
      </div>

      @if (error()) { <div class="table-error" role="alert">{{ error() }}</div> }
      @if (!insights() && !loading() && !error()) {
        <p class="intro">{{ (projectId()
            ? 'An executive summary, risk explanations, actions and weekly status for this project'
            : 'One briefing across all projects: which deliveries are on track or at risk, the top risks portfolio-wide, this week's actions and a project-by-project status')
          + (aiStatus()?.available
            ? ', drafted by AI from the stored tasks and findings. Task data is sent to ' + aiStatus()!.provider + '.'
            : ', built from the deterministic findings. No data leaves this server.') }}</p>
      }

      @if (insights(); as data) {
        <div class="source-line" [class.ai]="data.source === 'AI'" [class.degraded]="degraded(data.status)" role="status">
          <lucide-icon [img]="data.source === 'AI' ? FileIcon : AlertIcon" [size]="14" />
          <strong>{{ sourceLabel(data) }}</strong>
          <span>{{ data.source === 'AI' ? (data.model + ' · ' + formatDateTime(data.generatedAt)) : statusLabel(data.status) }}</span>
        </div>
        @if (data.source !== 'AI') { <p class="status-message">{{ data.message }}</p> }

        <h3>Executive summary</h3>
        <ul class="statements">@for (statement of data.executiveSummary; track $index) { <ng-container *ngTemplateOutlet="statementTpl; context: { $implicit: statement }" /> }</ul>
        <ng-template #statementTpl let-s>
          <li>
            <span class="basis" [class.assumption]="s.basis === 'ASSUMPTION'">{{ titleCase(s.basis) }}</span>
            <span class="statement-text">{{ s.text }}</span>
            @if (s.supportingTaskIds.length) { <span class="task-ids">{{ s.supportingTaskIds.join(', ') }}</span> }
          </li>
        </ng-template>

        @if (data.riskExplanations.length) {
          <h3>Highest-priority risks</h3>
          @for (risk of data.riskExplanations; track risk.riskId) {
            <article class="risk-explanation">
              <div class="risk-head"><span [class]="'severity ' + risk.severity.toLowerCase()">{{ titleCase(risk.severity) }}</span><strong>{{ risk.title }}</strong></div>
              <ul class="statements">@for (statement of risk.explanation; track $index) { <ng-container *ngTemplateOutlet="statementTpl; context: { $implicit: statement }" /> }</ul>
            </article>
          }
        }

        @if (data.recommendedActions.length) {
          <h3>Recommended actions</h3>
          <ul class="statements">@for (action of data.recommendedActions; track $index) { <ng-container *ngTemplateOutlet="statementTpl; context: { $implicit: { text: action.action, basis: action.basis, supportingTaskIds: action.supportingTaskIds } }" /> }</ul>
        }

        <details class="weekly" [open]="true">
          <summary>{{ projectId() ? 'Draft weekly status report' : 'Draft portfolio status report' }}</summary>
          @for (section of weeklySections(data); track section.title) {
            @if (section.items.length) {
              <h4>{{ section.title }}</h4>
              <ul class="statements">@for (statement of section.items; track $index) { <ng-container *ngTemplateOutlet="statementTpl; context: { $implicit: statement }" /> }</ul>
            }
          }
        </details>

        @if (data.validationWarnings.length) {
          <details class="warnings"><summary>{{ data.validationWarnings.length }} item(s) removed or relabelled by validation</summary>
            <ul>@for (warning of data.validationWarnings; track $index) { <li>{{ warning }}</li> }</ul>
          </details>
        }
        <p class="disclaimer">{{ data.disclaimer }}</p>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .surface { margin-top: 18px; padding: 20px; border: 1px solid #e3e8e2; border-radius: 8px; background: #fff; }
    .section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
    .eyebrow { color: #7d8b80; font-size: 9px; font-weight: 700; letter-spacing: .12em; }
    h2 { margin: 4px 0 0; color: #26352c; font-size: 16px; }
    h3 { margin: 18px 0 8px; color: #3c4d42; font-size: 12px; }
    h4 { margin: 12px 0 6px; color: #53645a; font-size: 11px; }
    .heading-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .ai-chip { padding: 5px 8px; border-radius: 999px; color: #6c776f; background: #f0f2ef; font-size: 9px; font-weight: 700; }
    .ai-chip.on { color: #3f6a35; background: #eaf4e2; }
    .button { display: inline-flex; min-height: 34px; align-items: center; gap: 8px; padding: 0 12px; border: 1px solid #dfe5de; border-radius: 6px; color: #3c5146; background: #fff; font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; }
    .button:disabled { opacity: .55; cursor: not-allowed; }
    .spinner { width: 12px; height: 12px; border: 2px solid #cfd8cd; border-top-color: #5f7d52; border-radius: 50%; animation: spin .8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .intro, .status-message { margin: 12px 0 0; color: #6f7b73; font-size: 11px; line-height: 1.5; }
    .table-error { margin-top: 12px; padding: 10px; border-radius: 5px; color: #ad5142; background: #fff4f0; font-size: 11px; }
    .source-line { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; margin-top: 14px; padding: 9px 11px; border-radius: 6px; color: #56645b; background: #f3f5f2; font-size: 10px; }
    .source-line.ai { color: #40613a; background: #eef6e8; }
    .source-line.degraded { color: #8a5a22; background: #fbf3e6; }
    .statements { display: grid; gap: 7px; margin: 0; padding: 0; list-style: none; }
    .statements li { display: flex; flex-wrap: wrap; align-items: baseline; gap: 7px; color: #34443a; font-size: 11px; line-height: 1.5; }
    .statement-text { flex: 1 1 260px; min-width: 0; overflow-wrap: anywhere; }
    .basis { flex: none; padding: 2px 6px; border-radius: 4px; color: #3f6a35; background: #eaf4e2; font-size: 8px; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }
    .basis.assumption { color: #8a5a22; background: #fbf0dc; }
    .task-ids { color: #7a8a7f; font-size: 9px; font-weight: 700; }
    .risk-explanation { padding: 10px 0; border-bottom: 1px solid #edf0ec; }
    .risk-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 11px; color: #2f3e35; }
    .severity { padding: 2px 6px; border-radius: 4px; font-size: 8px; font-weight: 800; text-transform: uppercase; color: #5d6b62; background: #eef1ed; }
    .severity.critical { color: #fff; background: #d95a48; } .severity.high { color: #9a4a1d; background: #fbe6d8; } .severity.medium { color: #7d6215; background: #fbf1cf; }
    .weekly, .warnings { margin-top: 16px; padding: 10px 12px; border: 1px solid #edf0ec; border-radius: 6px; }
    summary { color: #3c4d42; font-size: 11px; font-weight: 700; cursor: pointer; }
    .warnings ul { margin: 8px 0 0; padding-left: 18px; color: #7b6a4b; font-size: 10px; }
    .disclaimer { margin: 14px 0 0; color: #8a948c; font-size: 9px; }
  `],
})
export class AiInsightsPanelComponent {
  /** Omit for the portfolio briefing across all projects. */
  readonly projectId = input<string | null>(null);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly aiStatus = signal<AiStatusResponse | null>(null);
  readonly insights = signal<ProjectInsightsResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  protected readonly RefreshIcon = RefreshCw;
  protected readonly FileIcon = FileText;
  protected readonly AlertIcon = TriangleAlert;
  protected readonly titleCase = titleCase;
  protected readonly formatDateTime = formatDateTime;
  protected readonly sourceLabel = sourceLabel;
  protected readonly statusLabel = insightStatusLabel;
  protected readonly degraded = isDegraded;

  constructor() {
    // A status failure only hides the AI chip; the deterministic summary still works.
    this.api.getAiStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (status) => this.aiStatus.set(status),
      error: () => this.aiStatus.set(null),
    });
  }

  generate(): void {
    this.loading.set(true); this.error.set('');
    const projectId = this.projectId();
    const request = projectId ? this.api.generateInsights(projectId) : this.api.generatePortfolioInsights();
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => { this.insights.set(data); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(errorMessage(error)); this.loading.set(false); },
    });
  }

  weeklySections(data: ProjectInsightsResponse): { title: string; items: InsightStatement[] }[] {
    return [
      { title: this.projectId() ? 'Summary' : 'Project by project', items: data.weeklyStatus.summary },
      { title: 'Progress', items: data.weeklyStatus.progress },
      { title: 'Risks and issues', items: data.weeklyStatus.risksAndIssues },
      { title: 'Next steps', items: data.weeklyStatus.nextSteps },
    ];
  }
}
