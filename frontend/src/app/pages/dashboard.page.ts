import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideArrowUpRight as ArrowUpRight,
  LucideClock3 as Clock3,
  LucideFolderKanban as FolderKanban,
  LucideRefreshCw as RefreshCw,
  LucideTriangleAlert as TriangleAlert,
} from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PortfolioRiskSummary, ProjectRiskSummary } from '../core/api.models';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { LucideIconCompatComponent } from '../shared/lucide-icon-compat.component';
import { SeverityChartComponent } from '../shared/severity-chart.component';
import { AnalyzeAllButtonComponent } from '../shared/analyze-all-button.component';
import { AiInsightsPanelComponent } from '../shared/ai-insights-panel.component';
import { errorMessage, formatDate, formatDateTime } from '../shared/view-helpers';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, LucideIconCompatComponent, SeverityChartComponent, AnalyzeAllButtonComponent, AiInsightsPanelComponent],
  template: `
    <section class="page-heading">
      <div>
        <div class="eyebrow">PORTFOLIO OVERVIEW</div>
        <h1>Delivery health</h1>
        <p class="heading-copy">A live view of project progress, delivery pressure, and active findings.</p>
      </div>
      <div class="heading-actions">
        @if (portfolio()) { <span class="as-of">As of {{ portfolio()!.analysisDate | date:'MMM d, y' }}</span> }
        @if (portfolio()?.projectCount) { <app-analyze-all-button [projectIds]="projectIds(portfolio()!)" (finished)="refresh()" /> }
        <button class="button button-secondary" type="button" (click)="refresh()" [disabled]="loading()">
          <lucide-icon [img]="RefreshIcon" [size]="15" /> Refresh
        </button>
      </div>
    </section>

    @if (loading() && !portfolio()) {
      <div class="state-panel"><span class="spinner"></span><span>Loading portfolio data…</span></div>
    } @else if (error()) {
      <div class="state-panel state-error" role="alert"><div><strong>Portfolio unavailable</strong><span>{{ error() }}</span></div><button class="button button-secondary" (click)="refresh()">Try again</button></div>
    } @else if (portfolio(); as data) {
      @if (data.projectCount === 0) {
        <div class="empty-panel"><div class="empty-icon"><lucide-icon [img]="ProjectsIcon" [size]="20" /></div><h2>No project data yet</h2><p>Import an Excel workbook or CSV to populate portfolio metrics.</p><a class="button button-primary" routerLink="/upload">Upload project data</a></div>
      } @else {
        @if (staleProjects(data).length) {
          <div class="notice" role="status">Not analyzed on {{ formatDate(data.analysisDate) }}: {{ staleProjects(data).join(', ') }}. Their overdue counts and risks are as of their last analysis. Run analysis for a same-day picture.</div>
        }
        <div class="metric-grid">
          <article class="metric-card"><div class="metric-label">Projects</div><div class="metric-value">{{ data.projectCount }}</div><div class="metric-foot"><lucide-icon [img]="ProjectsIcon" [size]="13" /> Active portfolio</div><span class="metric-accent accent-green"></span></article>
          <article class="metric-card"><div class="metric-label">Tasks</div><div class="metric-value">{{ data.taskCount }}</div><div class="metric-foot">{{ data.completedTaskCount }} complete <span class="foot-divider">/</span> {{ data.incompleteTaskCount }} not done</div><span class="metric-accent accent-ochre"></span></article>
          <article class="metric-card"><div class="metric-label">Overdue</div><div class="metric-value" [class.value-alert]="data.overdueTaskCount > 0">{{ data.overdueTaskCount }}</div><div class="metric-foot">Incomplete past due date</div><span class="metric-accent accent-rust"></span></article>
          <article class="metric-card"><div class="metric-label">Blocked</div><div class="metric-value" [class.value-alert]="data.blockedTaskCount > 0">{{ data.blockedTaskCount }}</div><div class="metric-foot">Explicitly marked blocked</div><span class="metric-accent accent-blue"></span></article>
        </div>

        <div class="dashboard-grid">
          <section class="surface severity-panel">
            <div class="section-heading"><div><div class="eyebrow">ACTIVE FINDINGS</div><h2>Risk distribution</h2></div><a routerLink="/risks" class="text-link">View all <lucide-icon [img]="ArrowIcon" [size]="14" /></a></div>
            <div class="severity-content">
              <div class="chart-wrap"><app-severity-chart [values]="data.risksBySeverity" /><div class="chart-center"><strong>{{ data.activeRiskCount }}</strong><span>active risks</span></div></div>
              <div class="severity-legend">
                @for (item of severityItems(data); track item.name) {
                  <div class="legend-row"><span class="legend-dot" [class]="'legend-dot ' + item.name.toLowerCase()"></span><span>{{ item.name }}</span><strong>{{ item.count }}</strong></div>
                }
              </div>
            </div>
            <div class="analysis-stamp"><lucide-icon [img]="ClockIcon" [size]="14" /><span>Portfolio snapshot</span><strong>{{ formatDateTime(data.analysisTimestamp) }}</strong></div>
          </section>

          <section class="surface comparison-panel">
            <div class="section-heading"><div><div class="eyebrow">PROJECT COMPARISON</div><h2>Health at a glance</h2></div><a routerLink="/projects" class="text-link">Projects <lucide-icon [img]="ArrowIcon" [size]="14" /></a></div>
            <div class="comparison-list">
              @for (project of sortedProjects(data); track project.projectId) {
                <a class="comparison-row" [routerLink]="['/projects', project.projectId]">
                  <div class="project-monogram">{{ project.projectName.slice(0, 1) }}</div>
                  <div class="comparison-main"><div class="comparison-title"><strong>{{ project.projectName }}</strong><span>{{ project.activeRiskCount }} risk{{ project.activeRiskCount === 1 ? '' : 's' }} · {{ project.analysisTimestamp ? 'as of ' + formatDate(project.analysisDate) : 'not analyzed' }}</span></div>
                    <div class="progress-track" [attr.aria-label]="completionLabel(project)"><span [style.width.%]="completionPercent(project)"></span></div>
                  </div>
                  <div class="comparison-rate">{{ completionPercent(project) }}%</div>
                  <lucide-icon class="row-arrow" [img]="ArrowIcon" [size]="14" />
                </a>
              }
            </div>
            <div class="comparison-foot">Completion ratio is based on tasks marked Done.</div>
          </section>
        </div>

        <section class="surface highest-risk">
          <div class="section-heading"><div><div class="eyebrow">ATTENTION REQUIRED</div><h2>Highest-risk projects</h2></div><a routerLink="/risks" class="text-link">Open risk analysis <lucide-icon [img]="ArrowIcon" [size]="14" /></a></div>
          <div class="risk-project-grid">
            @for (project of highestRiskProjects(data); track project.projectId) {
              <a class="risk-project" [routerLink]="['/projects', project.projectId]">
                <div class="risk-project-top"><span class="risk-project-mark"><lucide-icon [img]="AlertIcon" [size]="15" /></span><span>{{ project.activeRiskCount }} active</span></div>
                <h3>{{ project.projectName }}</h3>
                <div class="risk-counts"><span>{{ project.overdueTaskCount }} overdue</span><span>{{ project.blockedTaskCount }} blocked</span></div>
              </a>
            } @empty { <div class="quiet-empty">No active risks across analyzed projects.</div> }
          </div>
        </section>
        <app-ai-insights-panel class="portfolio-insights" />
      }
    }
    <div class="page-footer">Data is calculated from imported project records. No external project system is connected.</div>
  `,
  styles: [`
    :host { display: block; }
    .page-heading { display:flex; align-items:flex-end; justify-content:space-between; gap:20px; margin-bottom:26px; }
    .eyebrow { color:#829087; font-size:9px; font-weight:800; letter-spacing:1.35px; }
    h1 { margin:7px 0 6px; color:#23332d; font-size:27px; font-weight:720; }
    .heading-copy { margin:0; color:#77827a; font-size:12px; }
    .heading-actions { display:flex; align-items:center; gap:14px; }
    .as-of { color:#77827a; font-size:11px; }
    .button { display:inline-flex; min-height:36px; align-items:center; justify-content:center; gap:8px; padding:0 13px; border:1px solid transparent; border-radius:6px; font:inherit; font-size:11px; font-weight:700; text-decoration:none; cursor:pointer; }
    .button:disabled { opacity:.55; cursor:wait; }
    .button-secondary { border-color:#dfe5de; color:#3c5146; background:#fff; }
    .button-primary { color:#1d3329; background:#c1ef74; }
    .metric-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:13px; }
    .metric-card { position:relative; overflow:hidden; min-height:124px; padding:17px 18px; border:1px solid #e5e9e3; border-radius:7px; background:#fff; }
    .metric-label { color:#77827a; font-size:11px; font-weight:600; }
    .metric-value { margin-top:10px; color:#273830; font-size:31px; font-weight:700; line-height:1; }
    .value-alert { color:#bd5746; }
    .metric-foot { display:flex; align-items:center; gap:5px; margin-top:12px; color:#89928b; font-size:10px; }
    .foot-divider { color:#c7cec8; }
    .metric-accent { position:absolute; top:17px; right:18px; width:7px; height:7px; border-radius:50%; }
    .accent-green { background:#82a96f; }.accent-ochre { background:#d5b34e; }.accent-rust { background:#d46c55; }.accent-blue { background:#709a9b; }
    .dashboard-grid { display:grid; grid-template-columns:minmax(0,.92fr) minmax(0,1.08fr); gap:14px; margin-top:15px; }
    .surface { border:1px solid #e5e9e3; border-radius:7px; background:#fff; }
    .severity-panel,.comparison-panel { min-height:330px; padding:19px 20px 0; }
    .section-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; }
    h2 { margin:5px 0 0; color:#293a32; font-size:15px; font-weight:700; }
    .text-link { display:inline-flex; align-items:center; gap:5px; padding-top:4px; color:#607d50; font-size:10px; font-weight:700; text-decoration:none; }
    .severity-content { display:flex; align-items:center; justify-content:center; gap:38px; min-height:237px; }
    .chart-wrap { position:relative; width:190px; height:190px; }
    .chart-center { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; flex-direction:column; pointer-events:none; }
    .chart-center strong { color:#293a32; font-size:28px; line-height:1; }
    .chart-center span { margin-top:5px; color:#839087; font-size:9px; }
    .severity-legend { display:grid; gap:12px; min-width:115px; }
    .legend-row { display:grid; grid-template-columns:9px 1fr auto; align-items:center; gap:8px; color:#657169; font-size:10px; }
    .legend-row strong { color:#35473d; font-size:11px; }
    .legend-dot { width:7px; height:7px; border-radius:50%; background:#a9c88c; }.legend-dot.critical { background:#d95a48; }.legend-dot.high { background:#e99b45; }.legend-dot.medium { background:#e6c75b; }
    .analysis-stamp { display:flex; align-items:center; gap:8px; padding:12px 0; border-top:1px solid #edf0ec; color:#849087; font-size:10px; }
    .analysis-stamp strong { margin-left:auto; color:#53655a; font-weight:650; }
    .comparison-list { display:grid; margin-top:20px; }
    .comparison-row { display:grid; grid-template-columns:30px minmax(0,1fr) 40px 16px; align-items:center; gap:11px; padding:12px 0; border-bottom:1px solid #edf0ec; color:inherit; text-decoration:none; }
    .project-monogram { display:grid; width:29px; height:29px; place-items:center; border-radius:6px; color:#435b48; background:#edf2e9; font-size:12px; font-weight:750; }
    .comparison-main { min-width:0; }.comparison-title { display:flex; justify-content:space-between; gap:8px; margin-bottom:7px; }.comparison-title strong { overflow:hidden; color:#33443a; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }.comparison-title span { flex:none; color:#89938c; font-size:9px; }
    .progress-track { overflow:hidden; height:4px; border-radius:4px; background:#edf0ec; }.progress-track span { display:block; height:100%; border-radius:4px; background:#89ad71; }
    .comparison-rate { color:#53645a; font-size:10px; font-weight:700; text-align:right; }.row-arrow { color:#9ca79f; }
    .comparison-foot { padding:11px 0; color:#909992; font-size:9px; }
    .portfolio-insights { display:block; margin-top:15px; }
    .highest-risk { margin-top:15px; padding:19px 20px 20px; }
    .risk-project-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:11px; margin-top:18px; }
    .risk-project { padding:13px; border:1px solid #ecefea; border-radius:6px; color:inherit; text-decoration:none; transition:border-color 140ms ease, transform 140ms ease; }.risk-project:hover { transform:translateY(-1px); border-color:#d8e1d2; }
    .risk-project-top { display:flex; align-items:center; justify-content:space-between; color:#9a6b43; font-size:9px; font-weight:700; }.risk-project-mark { display:grid; width:26px; height:26px; place-items:center; border-radius:6px; color:#bd694b; background:#fbefea; }
    .risk-project h3 { margin:13px 0 10px; color:#314239; font-size:12px; }.risk-counts { display:flex; gap:13px; color:#849087; font-size:9px; }
    .notice { margin-bottom:13px; padding:10px 12px; border:1px solid #ecdcb8; border-radius:5px; color:#7a5a1e; background:#fdf8ec; font-size:11px; }
    .quiet-empty { grid-column:1/-1; padding:18px 0; color:#829087; font-size:11px; }
    .page-footer { padding:16px 2px 0; color:#97a098; font-size:9px; }
    .state-panel,.empty-panel { display:flex; min-height:240px; align-items:center; justify-content:center; gap:12px; border:1px solid #e5e9e3; border-radius:7px; color:#718077; background:#fff; font-size:12px; }
    .state-error { justify-content:space-between; padding:25px; color:#a34f40; background:#fffaf8; }.state-error div { display:grid; gap:6px; }.state-error span { color:#7d716d; }
    .spinner { width:16px; height:16px; border:2px solid #dce5d8; border-top-color:#6d8a58; border-radius:50%; animation:spin .7s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
    .empty-panel { flex-direction:column; text-align:center; }.empty-panel h2 { margin:0; }.empty-panel p { margin:0 0 8px; color:#7c877f; font-size:11px; }.empty-icon { display:grid; width:40px; height:40px; place-items:center; border-radius:50%; color:#617e4e; background:#eef4e8; }
    @media (max-width:1050px) { .dashboard-grid { grid-template-columns:1fr; }.severity-panel,.comparison-panel { min-height:0; } }
    @media (max-width:720px) { .page-heading { align-items:flex-start; flex-direction:column; }.heading-actions { width:100%; justify-content:space-between; }.metric-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.metric-card { min-height:112px; padding:14px; }.metric-value { font-size:27px; }.severity-content { gap:20px; }.chart-wrap { width:160px; height:160px; }.risk-project-grid { grid-template-columns:1fr; } }
    @media (max-width:380px) { .severity-content { gap:8px; }.chart-wrap { width:140px; height:140px; }.severity-legend { min-width:100px; } }
  `],
})
export class DashboardPage {
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly portfolio = signal<PortfolioRiskSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  protected readonly RefreshIcon = RefreshCw;
  protected readonly ProjectsIcon = FolderKanban;
  protected readonly ArrowIcon = ArrowUpRight;
  protected readonly ClockIcon = Clock3;
  protected readonly AlertIcon = TriangleAlert;
  protected readonly formatDate = formatDate;
  protected readonly formatDateTime = formatDateTime;

  constructor() { this.refresh(); }

  refresh(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getPortfolio().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => { this.portfolio.set(data); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(errorMessage(error)); this.loading.set(false); },
    });
  }

  severityItems(data: PortfolioRiskSummary): { name: string; count: number }[] {
    return (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const)
      .map((name) => ({ name, count: data.risksBySeverity[name] ?? 0 }));
  }

  completionPercent(project: ProjectRiskSummary): number {
    return project.taskCount ? Math.round((project.completedTaskCount / project.taskCount) * 100) : 0;
  }

  completionLabel(project: ProjectRiskSummary): string {
    return `${project.completedTaskCount} of ${project.taskCount} tasks complete`;
  }

  sortedProjects(data: PortfolioRiskSummary): ProjectRiskSummary[] {
    return [...data.projects].sort((a, b) => b.activeRiskCount - a.activeRiskCount || a.projectName.localeCompare(b.projectName));
  }

  projectIds(data: PortfolioRiskSummary): string[] {
    return data.projects.map((project) => project.projectId);
  }

  staleProjects(data: PortfolioRiskSummary): string[] {
    return data.projects
      .filter((project) => !project.analysisTimestamp || project.analysisDate !== data.analysisDate)
      .map((project) => project.projectName);
  }

  highestRiskProjects(data: PortfolioRiskSummary): ProjectRiskSummary[] {
    return this.sortedProjects(data).filter((project) => project.activeRiskCount > 0).slice(0, 3);
  }
}