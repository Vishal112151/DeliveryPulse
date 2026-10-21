import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideRefreshCw as RefreshCw, LucideTriangleAlert as TriangleAlert } from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PortfolioRiskSummary, ProjectRiskSummary, RiskCategory, RiskResponse, RiskSeverity } from '../core/api.models';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { RiskCardsComponent } from '../shared/risk-cards.component';
import { AnalyzeAllButtonComponent } from '../shared/analyze-all-button.component';
import { LucideIconCompatComponent } from '../shared/lucide-icon-compat.component';
import { errorMessage, titleCase } from '../shared/view-helpers';

interface RiskEntry { projectId: string; projectName: string; risk: RiskResponse; }

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LucideIconCompatComponent, RiskCardsComponent, AnalyzeAllButtonComponent],
  template: `
    <section class="page-heading"><div><div class="eyebrow">DELIVERY SIGNALS</div><h1>Risk analysis</h1><p class="heading-copy">Evidence-backed findings from the current project records.</p></div><div class="heading-actions">@if (portfolio()?.projectCount) { <app-analyze-all-button [projectIds]="projectIds(portfolio()!)" (finished)="refresh()" /> }<button class="button button-secondary" type="button" (click)="refresh()" [disabled]="loading()"><lucide-icon [img]="RefreshIcon" [size]="15" /> Refresh findings</button></div></section>
    @if (loading() && !portfolio()) { <div class="state-panel"><span class="spinner"></span>Loading active risks…</div> }
    @else if (error()) { <div class="state-error" role="alert"><strong>Risk findings unavailable</strong><span>{{ error() }}</span><button class="button button-secondary" (click)="refresh()">Try again</button></div> }
    @else if (portfolio(); as data) {
      <section class="risk-overview">
        <div><div class="eyebrow">PORTFOLIO FINDINGS</div><strong>{{ data.activeRiskCount }}</strong><span>active risks across {{ data.projectCount }} projects</span></div>
        <div class="risk-overview-severity">@for (severity of severities; track severity) { <div><span [class]="'severity-dot ' + severity.toLowerCase()"></span><strong>{{ data.risksBySeverity[severity] ?? 0 }}</strong><small>{{ titleCase(severity) }}</small></div> }</div>
      </section>
      <section class="surface filters">
        <label><span>Project</span><select [(ngModel)]="projectFilter" aria-label="Filter by project"><option value="">All projects</option>@for (project of data.projects; track project.projectId) { <option [value]="project.projectId">{{ project.projectName }}</option> }</select></label>
        <label><span>Severity</span><select [(ngModel)]="severityFilter" aria-label="Filter by severity"><option value="">All severities</option>@for (severity of severities; track severity) { <option [value]="severity">{{ titleCase(severity) }}</option> }</select></label>
        <label><span>Category</span><select [(ngModel)]="categoryFilter" aria-label="Filter by category"><option value="">All categories</option>@for (category of categories; track category) { <option [value]="category">{{ titleCase(category) }}</option> }</select></label>
        <span class="filter-result">{{ filteredRisks(data).length }} findings</span>
      </section>
      <section class="surface findings-surface">
        @if (analysisError()) { <div class="analysis-error" role="alert">{{ analysisError() }}</div> }
        @if (!filteredRisks(data).length) { <div class="empty-state"><div class="empty-icon"><lucide-icon [img]="AlertIcon" [size]="19" /></div><strong>{{ data.activeRiskCount ? 'No findings match these filters' : 'No active findings' }}</strong><span>{{ data.activeRiskCount ? 'Adjust the selected project, severity, or category.' : 'Run analysis from a project to calculate current risk signals.' }}</span></div> }
        @else {
          @for (group of groupedRisks(data); track group.projectId) {
            <div class="project-risk-group"><div class="project-risk-heading"><a [routerLink]="['/projects', group.projectId]">{{ group.projectName }}</a><span>{{ group.risks.length }} finding{{ group.risks.length === 1 ? '' : 's' }}</span><button class="text-button" type="button" (click)="analyze(group.projectId)" [disabled]="analyzingProject() === group.projectId"><lucide-icon [img]="RefreshIcon" [size]="13" />{{ analyzingProject() === group.projectId ? 'Analyzing…' : 'Run analysis' }}</button></div>
              <app-risk-cards [risks]="group.risks" />
            </div>
          }
        }
      </section>
      <p class="method-note"><lucide-icon [img]="AlertIcon" [size]="14" /> Potential findings are not confirmed schedule failures. Milestone risk is not assessed because no explicit milestone date is currently modeled.</p>
    }
  `,
  styles: [`
    :host{display:block}.heading-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:15px;margin-bottom:24px}.eyebrow{color:#829087;font-size:11px;font-weight:800;letter-spacing:1.35px}h1{margin:7px 0 6px;color:#23332d;font-size:27px;font-weight:720}.heading-copy{margin:0;color:#77827a;font-size:14.5px}.button{display:inline-flex;min-height:36px;align-items:center;justify-content:center;gap:8px;padding:0 13px;border:1px solid #dfe5de;border-radius:6px;color:#3c5146;background:#fff;font:inherit;font-size:12px;font-weight:700;cursor:pointer}.button:disabled{opacity:.5;cursor:wait}.risk-overview{display:flex;align-items:center;justify-content:space-between;gap:25px;padding:17px 20px;border:1px solid #e5e9e3;border-radius:7px;background:#fff}.risk-overview>div:first-child{display:grid;grid-template-columns:auto 1fr;align-items:center;column-gap:12px}.risk-overview .eyebrow{grid-column:1/-1;margin-bottom:7px}.risk-overview>div:first-child strong{color:#293a32;font-size:29px;line-height:1}.risk-overview>div:first-child span{color:#879189;font-size:12px}.risk-overview-severity{display:flex;gap:20px}.risk-overview-severity>div{display:grid;grid-template-columns:7px auto;align-items:center;gap:3px 6px}.severity-dot{width:7px;height:7px;border-radius:50%;background:#a9c88c}.severity-dot.critical{background:#d95a48}.severity-dot.high{background:#e99b45}.severity-dot.medium{background:#e6c75b}.risk-overview-severity strong{color:#3d4d43;font-size:14.5px}.risk-overview-severity small{grid-column:2;color:#929a93;font-size:9.5px}.surface{border:1px solid #e5e9e3;border-radius:7px;background:#fff}.filters{display:flex;align-items:flex-end;gap:12px;margin-top:13px;padding:12px 14px}.filters label{display:grid;gap:5px}.filters label span{color:#859088;font-size:9.5px;font-weight:750;text-transform:uppercase;letter-spacing:.5px}.filters select{height:33px;min-width:145px;padding:0 9px;border:1px solid #e0e6df;border-radius:4px;color:#45574c;background:#fff;font:inherit;font-size:12px}.filter-result{margin:0 0 9px auto;color:#89938c;font-size:11px}.findings-surface{margin-top:13px;padding:0 18px}.project-risk-group+.project-risk-group{border-top:1px solid #e9ede8}.project-risk-heading{display:flex;align-items:center;gap:10px;padding-top:17px}.project-risk-heading>a{color:#34493a;font-size:14.5px;font-weight:750;text-decoration:none}.project-risk-heading>span{color:#939b94;font-size:11px}.text-button{display:inline-flex;align-items:center;gap:5px;margin-left:auto;padding:5px 7px;border:0;color:#5d7b4d;background:transparent;font:inherit;font-size:11px;font-weight:700;cursor:pointer}.text-button:disabled{opacity:.6}.empty-state{display:flex;min-height:230px;align-items:center;justify-content:center;flex-direction:column;gap:8px;color:#7d8980;text-align:center}.empty-state strong{margin-top:4px;color:#3b4c42;font-size:15.5px}.empty-state>span{font-size:12px}.empty-icon{display:grid;width:40px;height:40px;place-items:center;border-radius:50%;color:#a06c42;background:#fbf1e4}.method-note{display:flex;align-items:flex-start;gap:8px;margin:13px 2px;color:#817a66;font-size:11px;line-height:1.5}.method-note lucide-icon{flex:none;color:#a48b47}.state-panel,.state-error{display:flex;min-height:200px;align-items:center;justify-content:center;gap:10px;color:#77827a;font-size:13px}.state-error{flex-direction:column;color:#a55041}.state-error span{color:#82746e}.spinner{width:14px;height:14px;border:2px solid #dce5d8;border-top-color:#6d8a58;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:700px){.page-heading{align-items:flex-start;flex-direction:column}.risk-overview{align-items:flex-start;flex-direction:column}.risk-overview-severity{width:100%;justify-content:space-between;gap:8px}.filters{display:grid;grid-template-columns:1fr 1fr}.filters select{min-width:0;width:100%}.filter-result{grid-column:1/-1;margin:0}.findings-surface{padding-inline:12px}}
  `, '.analysis-error{margin:12px 0 0;padding:10px 12px;border-radius:4px;color:#a34f40;background:#fff3ef;font-size:12px;}'],
})
export class RisksPage {
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly portfolio = signal<PortfolioRiskSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly analysisError = signal('');
  readonly analyzingProject = signal('');
  projectFilter = '';
  severityFilter = '';
  categoryFilter = '';
  protected readonly RefreshIcon = RefreshCw;
  protected readonly AlertIcon = TriangleAlert;
  protected readonly titleCase = titleCase;
  protected readonly severities: RiskSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  protected readonly categories: RiskCategory[] = ['OVERDUE_TASK', 'BLOCKED_TASK', 'CRITICAL_TASK_AT_RISK', 'DEPENDENCY_RISK', 'DELIVERY_RISK', 'DEFECT_RISK', 'RECURRING_BLOCKER', 'DATA_QUALITY_WARNING'];

  constructor() { this.refresh(); }
  refresh(): void {
    this.loading.set(true); this.error.set('');
    this.api.getPortfolio().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (portfolio) => { this.portfolio.set(portfolio); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(errorMessage(error)); this.loading.set(false); },
    });
  }
  projectIds(data: PortfolioRiskSummary): string[] {
    return data.projects.map((project) => project.projectId);
  }
  filteredRisks(data: PortfolioRiskSummary): RiskEntry[] {
    return data.projects.flatMap((project) => project.risks
      .filter((risk) => !this.projectFilter || project.projectId === this.projectFilter)
      .filter((risk) => !this.severityFilter || risk.severity === this.severityFilter)
      .filter((risk) => !this.categoryFilter || risk.category === this.categoryFilter)
      .map((risk) => ({ projectId: project.projectId, projectName: project.projectName, risk })));
  }
  groupedRisks(data: PortfolioRiskSummary): { projectId: string; projectName: string; risks: RiskResponse[] }[] {
    return data.projects.map((project) => ({ ...project, risks: this.filteredRisks(data)
      .filter((entry) => entry.projectId === project.projectId).map((entry) => entry.risk) }))
      .filter((group) => group.risks.length > 0);
  }
  analyze(projectId: string): void {
    this.analyzingProject.set(projectId);
    this.analysisError.set('');
    this.api.analyzeProject(projectId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.analyzingProject.set(''); this.refresh(); },
      error: (error: unknown) => { this.analyzingProject.set(''); this.analysisError.set(errorMessage(error)); },
    });
  }
}