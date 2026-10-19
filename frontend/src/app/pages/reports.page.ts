import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideDownload as Download, LucidePrinter as Printer } from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WeeklyActivity, WeeklyReportResponse } from '../core/api.models';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { RiskCardsComponent } from '../shared/risk-cards.component';
import { LucideIconCompatComponent } from '../shared/lucide-icon-compat.component';
import { csvCell } from './report-csv';
import { errorMessage, formatDate, formatDateTime, titleCase } from '../shared/view-helpers';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, LucideIconCompatComponent, RiskCardsComponent],
  template: `
    <section class="page-heading no-print"><div><div class="eyebrow">STATUS COMMUNICATION</div><h1>Weekly report</h1><p class="heading-copy">A printable portfolio snapshot built from current project records.</p></div><div class="report-actions"><button class="button button-secondary" type="button" (click)="print()"><lucide-icon [img]="PrintIcon" [size]="15" /> Print report</button><button class="button button-primary" type="button" (click)="download()" [disabled]="!report()"><lucide-icon [img]="DownloadIcon" [size]="15" /> Download CSV</button></div></section>
    @if (loading()) { <div class="state-panel no-print"><span class="spinner"></span>Preparing weekly report…</div> }
    @else if (error()) { <div class="state-error no-print" role="alert"><strong>Report unavailable</strong><span>{{ error() }}</span><button class="button button-secondary" (click)="load()">Try again</button></div> }
    @else if (report(); as data) {
      <article class="report-sheet">
        <header class="report-title"><div><div class="eyebrow">DELIVERYPULSE · INTERNAL STATUS</div><h2>Portfolio delivery report</h2><p>{{ formatDate(data.weekStartDate) }} — {{ formatDate(data.weekEndDate) }}</p></div><div class="generated"><span>Generated</span><strong>{{ formatDateTime(data.analysisTimestamp) }}</strong><span>Analysis date · {{ formatDate(data.analysisDate) }}</span></div></header>
        <div class="report-metrics"><div><span>Projects</span><strong>{{ data.projectCount }}</strong></div><div><span>Tasks</span><strong>{{ data.taskCount }}</strong></div><div><span>Complete</span><strong>{{ data.completedTaskCount }}</strong></div><div><span>Overdue</span><strong [class.alert]="data.overdueTaskCount">{{ data.overdueTaskCount }}</strong></div><div><span>Blocked</span><strong [class.alert]="data.blockedTaskCount">{{ data.blockedTaskCount }}</strong></div><div><span>Open defects</span><strong [class.alert]="data.openDefectCount">{{ data.openDefectCount }}</strong></div><div><span>Active risks</span><strong>{{ data.activeRiskCount }}</strong></div></div>
        <section class="report-section"><div class="report-section-heading"><div><div class="eyebrow">THIS WEEK</div><h3>Task movement recorded by imports</h3></div></div>
          <div class="activity-grid"><div><span>Added</span><strong>{{ data.activity.tasksAdded }}</strong></div><div><span>Completed</span><strong>{{ data.activity.tasksCompleted }}</strong></div><div><span>Newly blocked</span><strong [class.alert]="data.activity.tasksBlocked">{{ data.activity.tasksBlocked }}</strong></div><div><span>Unblocked</span><strong>{{ data.activity.tasksUnblocked }}</strong></div><div><span>Defects opened</span><strong [class.alert]="data.activity.defectsOpened">{{ data.activity.defectsOpened }}</strong></div><div><span>Defects closed</span><strong>{{ data.activity.defectsClosed }}</strong></div></div>
        </section>
        <section class="report-section"><div class="report-section-heading"><div><div class="eyebrow">PROJECT COMPARISON</div><h3>Delivery progress</h3></div></div>
          @if (!data.projects.length) { <div class="empty-line">No projects have been imported.</div> }
          @else { <div class="table-wrap"><table><thead><tr><th>Project</th><th>Tasks</th><th>Done</th><th>Overdue</th><th>Blocked</th><th>Defects</th><th>Done this week</th><th>Blocked this week</th><th>Risks</th><th>Latest analysis</th></tr></thead><tbody>@for (project of data.projects; track project.projectId) { <tr><td><a [routerLink]="['/projects', project.projectId]">{{ project.projectName }}</a></td><td>{{ project.taskCount }}</td><td>{{ project.completedTaskCount }}</td><td [class.alert]="project.overdueTaskCount">{{ project.overdueTaskCount }}</td><td [class.alert]="project.blockedTaskCount">{{ project.blockedTaskCount }}</td><td [class.alert]="project.openDefectCount">{{ project.openDefectCount }}</td><td>{{ activityFor(data, project.projectId).tasksCompleted }}</td><td [class.alert]="activityFor(data, project.projectId).tasksBlocked">{{ activityFor(data, project.projectId).tasksBlocked }}</td><td>{{ project.activeRiskCount }}</td><td>{{ project.analysisTimestamp ? formatDateTime(project.analysisTimestamp) : 'Not analyzed' }}</td></tr> }</tbody></table></div> }
        </section>
        <section class="report-section"><div class="report-section-heading"><div><div class="eyebrow">RISK REGISTER</div><h3>Active findings and recommended actions</h3></div></div>
          @for (project of data.projects; track project.projectId) { @if (project.risks.length) { <div class="risk-group"><h4>{{ project.projectName }}</h4><app-risk-cards [risks]="project.risks" /></div> } }
          @if (!data.activeRiskCount) { <div class="empty-line">No active risk findings in this snapshot.</div> }
        </section>
        <footer class="report-foot">Totals are a current-data snapshot. Weekly movement comes from task status changes recorded when project files are re-uploaded with "Update existing projects"; changes between uploads are not visible.</footer>
      </article>
    }
  `,
  styles: [`
    :host{display:block}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:15px;margin-bottom:22px}.eyebrow{color:#829087;font-size:9px;font-weight:800;letter-spacing:1.35px}h1{margin:7px 0 6px;color:#23332d;font-size:27px;font-weight:720}.heading-copy{margin:0;color:#77827a;font-size:12px}.report-actions{display:flex;gap:8px}.button{display:inline-flex;min-height:36px;align-items:center;justify-content:center;gap:8px;padding:0 13px;border:1px solid #dfe5de;border-radius:6px;color:#3c5146;background:#fff;font:inherit;font-size:10px;font-weight:700;cursor:pointer}.button:disabled{opacity:.5;cursor:not-allowed}.button-primary{border-color:transparent;color:#1d3329;background:#c1ef74}.report-sheet{padding:29px 31px;border:1px solid #e3e8e1;border-radius:7px;background:#fff}.report-title{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;padding-bottom:20px;border-bottom:1px solid #e8ece7}.report-title h2{margin:7px 0 5px;color:#273a30;font-size:20px}.report-title p{margin:0;color:#7e8980;font-size:10px}.generated{display:grid;gap:4px;text-align:right}.generated span{color:#929b94;font-size:8px}.generated strong{color:#4a5b50;font-size:10px}.activity-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:10px}.activity-grid div{display:grid;gap:6px}.activity-grid span{color:#88928a;font-size:8px}.activity-grid strong{color:#33463a;font-size:16px}.report-metrics{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:10px;padding:16px 0;border-bottom:1px solid #e8ece7}.report-metrics div{display:grid;gap:6px}.report-metrics span{color:#88928a;font-size:8px}.report-metrics strong{color:#33463a;font-size:20px}.report-metrics strong.alert,.alert{color:#bf5948}.report-section{padding:19px 0;border-bottom:1px solid #e8ece7}.report-section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:13px}.report-section h3{margin:5px 0 0;color:#32453a;font-size:13px}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;min-width:660px}th{padding:9px 8px;border-bottom:1px solid #e7ebe6;color:#89938c;font-size:8px;text-align:left;text-transform:uppercase;letter-spacing:.45px}td{padding:10px 8px;border-bottom:1px solid #f0f2ef;color:#5e6b62;font-size:9px}td a{color:#426044;font-weight:700;text-decoration:none}.risk-group{margin-top:13px}.risk-group h4{margin:0;color:#405146;font-size:11px}.empty-line{padding:17px 0;color:#879189;font-size:10px}.report-foot{padding-top:14px;color:#8e9891;font-size:8px}.state-panel,.state-error{display:flex;min-height:220px;align-items:center;justify-content:center;gap:10px;color:#77827a;font-size:11px}.state-error{flex-direction:column;color:#a55041}.state-error span{color:#82746e}.spinner{width:14px;height:14px;border:2px solid #dce5d8;border-top-color:#6d8a58;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:700px){.page-heading{align-items:flex-start;flex-direction:column}.report-actions{width:100%}.report-sheet{padding:18px 14px}.report-title{flex-direction:column}.generated{text-align:left}.report-metrics,.activity-grid{grid-template-columns:repeat(3,1fr)}}
    @media print{ :host{color:#111;background:#fff}.no-print{display:none!important}.report-sheet{padding:0;border:0;border-radius:0}.report-title{break-inside:avoid}.report-section,.risk-group{break-inside:avoid}.report-metrics{grid-template-columns:repeat(7,1fr)}.report-foot{position:fixed;bottom:0} }
  `],
})
export class ReportsPage {
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly report = signal<WeeklyReportResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  protected readonly PrintIcon = Printer;
  protected readonly DownloadIcon = Download;
  protected readonly formatDate = formatDate;
  protected readonly formatDateTime = formatDateTime;
  protected readonly titleCase = titleCase;

  constructor() { this.load(); }
  load(): void {
    this.loading.set(true); this.error.set('');
    this.api.getWeeklyReport().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (report) => { this.report.set(report); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(errorMessage(error)); this.loading.set(false); },
    });
  }
  private static readonly NO_ACTIVITY: WeeklyActivity = { tasksAdded: 0, tasksCompleted: 0, tasksBlocked: 0, tasksUnblocked: 0, defectsOpened: 0, defectsClosed: 0 };
  activityFor(report: WeeklyReportResponse, projectId: string): WeeklyActivity {
    return report.activityByProject[projectId] ?? ReportsPage.NO_ACTIVITY;
  }
  print(): void { window.print(); }
  download(): void {
    const report = this.report();
    if (!report) return;
    const rows = [['Project', 'Tasks', 'Completed', 'Incomplete', 'Overdue', 'Blocked', 'Open defects', 'Active risks',
      'Added this week', 'Completed this week', 'Blocked this week', 'Unblocked this week', 'Defects opened this week',
      'Defects closed this week', 'Analysis date', 'Analysis timestamp']];
    for (const project of report.projects) {
      const activity = this.activityFor(report, project.projectId);
      rows.push([
        project.projectName, String(project.taskCount), String(project.completedTaskCount),
        String(project.incompleteTaskCount), String(project.overdueTaskCount), String(project.blockedTaskCount),
        String(project.openDefectCount), String(project.activeRiskCount), String(activity.tasksAdded),
        String(activity.tasksCompleted), String(activity.tasksBlocked), String(activity.tasksUnblocked),
        String(activity.defectsOpened), String(activity.defectsClosed), project.analysisDate, project.analysisTimestamp ?? '',
      ]);
    }
    const csv = rows.map((row) => row.map(csvCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `deliverypulse-weekly-${report.analysisDate}.csv`; anchor.click(); URL.revokeObjectURL(url);
  }
}