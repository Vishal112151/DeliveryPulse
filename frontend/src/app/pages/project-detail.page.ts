import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideArrowLeft as ArrowLeft, LucideArrowUpDown as ArrowUpDown, LucideRefreshCw as RefreshCw, LucideTriangleAlert as TriangleAlert } from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Priority,
  ProjectResponse,
  ProjectRiskSummary,
  TaskPageResponse,
  TaskQuery,
  TaskSortDirection,
  TaskSortField,
  TaskStatus,
} from '../core/api.models';
import { AuthService } from '../core/auth.service';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { RiskCardsComponent } from '../shared/risk-cards.component';
import { HealthBadgeComponent } from '../shared/project-health';
import { AiInsightsPanelComponent } from '../shared/ai-insights-panel.component';
import { LucideIconCompatComponent } from '../shared/lucide-icon-compat.component';
import { errorMessage, formatDate, formatDateTime, titleCase } from '../shared/view-helpers';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LucideIconCompatComponent, RiskCardsComponent, AiInsightsPanelComponent, HealthBadgeComponent],
  template: `
    <a class="back-link" routerLink="/projects"><lucide-icon [img]="BackIcon" [size]="14" /> All projects</a>
    @if (loadingProject() && !project()) { <div class="state-panel"><span class="spinner"></span>Loading project…</div> }
    @else if (projectError()) { <div class="state-error" role="alert"><strong>Project unavailable</strong><span>{{ projectError() }}</span></div> }
    @else if (project(); as item) {
      <section class="page-heading"><div><div class="eyebrow">PROJECT DETAIL</div><h1>{{ item.name }}</h1><app-health-badge class="detail-health" [risksBySeverity]="item.risksBySeverity" [analyzed]="item.analysisTimestamp !== null" [showReason]="true" /><p class="heading-copy">{{ item.description || item.sourceSheetName || 'Project delivery overview' }}</p></div>@if (auth.canEdit()) { <button class="button button-secondary" type="button" (click)="analyze()" [disabled]="analyzing()">@if (analyzing()) { <span class="spinner"></span>Analyzing… } @else { <lucide-icon [img]="RefreshIcon" [size]="15" /> Run analysis }</button> }</section>

      <div class="summary-grid">
        <article class="summary-metric"><span>Tasks</span><strong>{{ item.taskCount }}</strong><small>{{ item.completedTaskCount }} completed</small></article>
        <article class="summary-metric"><span>Incomplete</span><strong>{{ item.incompleteTaskCount }}</strong><small>Not started, in progress or blocked</small></article>
        <article class="summary-metric"><span>Overdue</span><strong [class.alert-value]="item.overdueTaskCount">{{ item.overdueTaskCount }}</strong><small>Before analysis date</small></article>
        <article class="summary-metric"><span>Blocked</span><strong [class.alert-value]="item.blockedTaskCount">{{ item.blockedTaskCount }}</strong><small>Explicit Blocked status</small></article>
        <article class="summary-metric"><span>Open defects</span><strong [class.alert-value]="item.openDefectCount">{{ item.openDefectCount }}</strong><small>Type Bug or Defect, not Done</small></article>
        <article class="summary-metric risk-summary"><span>Active risks</span><strong>{{ riskSummary()?.activeRiskCount ?? riskTotal(item) }}</strong><div class="severity-inline">@for (severity of severities; track severity) { @if (item.risksBySeverity[severity]) { <span [class]="'severity-pill ' + severity.toLowerCase()">{{ item.risksBySeverity[severity] }} {{ severity.toLowerCase() }}</span> } }</div></article>
      </div>

      @if (analysisNotice()) { <div class="notice" role="status">{{ analysisNotice() }}</div> }
      @if (riskSummary()?.milestones.length) {
        <section class="surface milestone-surface"><div class="section-heading"><div><div class="eyebrow">DELIVERY PLAN</div><h2>Milestones</h2></div><span class="last-analysis">As of {{ formatDate(riskSummary()!.analysisDate) }}</span></div>
          <div class="table-scroll"><table class="milestone-table"><thead><tr><th>Milestone</th><th>Date</th><th>Done</th><th>Blocked</th><th>Open defects</th><th>Status</th></tr></thead><tbody>
            @for (milestone of riskSummary()!.milestones; track milestone.name) {
              <tr><td><strong class="task-id">{{ milestone.name }}</strong></td><td>{{ formatDate(milestone.milestoneDate) }}</td><td>{{ milestone.completedTaskCount }} / {{ milestone.taskCount }}</td><td [class.overdue-date]="milestone.blockedTaskCount">{{ milestone.blockedTaskCount }}</td><td [class.overdue-date]="milestone.openDefectCount">{{ milestone.openDefectCount }}</td><td><span [class]="'milestone-tag ' + milestone.status.toLowerCase()">{{ titleCase(milestone.status) }}</span></td></tr>
            }
          </tbody></table></div>
        </section>
      }
      <section class="surface task-surface">
        <div class="section-heading"><div><div class="eyebrow">WORK BREAKDOWN</div><h2>Tasks</h2></div><span class="last-analysis">Analysis: {{ item.analysisTimestamp ? (item.analysisTimestamp | date:'MMM d, y, h:mm a') : 'not run' }}</span></div>
        <form class="filter-bar" (submit)="$event.preventDefault(); pageIndex.set(0); loadTasks()">
          <label><span>Status</span><select name="status" [(ngModel)]="statusFilter"><option value="">All statuses</option>@for (status of statuses; track status) { <option [value]="status">{{ titleCase(status) }}</option> }</select></label>
          <label><span>Priority</span><select name="priority" [(ngModel)]="priorityFilter"><option value="">All priorities</option>@for (priority of priorities; track priority) { <option [value]="priority">{{ titleCase(priority) }}</option> }</select></label>
          <label class="assignee-filter"><span>Assignee</span><input name="assignee" [(ngModel)]="assigneeFilter" placeholder="Any assignee" /></label>
          <label><span>Overdue</span><select name="overdue" [(ngModel)]="overdueFilter"><option value="">Any</option><option value="true">Overdue</option><option value="false">Not overdue</option></select></label>
          <label><span>Sort</span><select name="sortBy" [(ngModel)]="sortBy"><option value="TASK_ID">Task ID</option><option value="TASK_NAME">Task name</option><option value="STATUS">Status</option><option value="PRIORITY">Priority</option><option value="ASSIGNEE">Assignee</option><option value="DUE_DATE">Due date</option></select></label>
          <button class="icon-button sort-direction" type="button" [attr.aria-label]="sortDirection === 'ASC' ? 'Ascending sort, switch to descending' : 'Descending sort, switch to ascending'" (click)="toggleSort()"><lucide-icon [img]="SortIcon" [size]="15" /><span>{{ sortDirection }}</span></button>
          <button class="button button-primary apply-button" type="submit">Apply</button>
        </form>

        @if (taskError()) { <div class="table-error" role="alert">{{ taskError() }}</div> }
        @if (tasksLoading()) { <div class="state-line"><span class="spinner"></span>Loading task records…</div> }
        @else if (taskPage(); as result) {
          @if (result.content.length === 0) { <div class="empty-table">No tasks match these filters.</div> }
          @else {
            <div class="table-scroll"><table><thead><tr><th>Task</th><th>Assignee</th><th>Priority</th><th>Status</th><th>Due date</th><th>Blocker / dependency text</th></tr></thead><tbody>
              @for (task of result.content; track task.id) {
                <tr><td><strong class="task-id">{{ task.taskId }}@if (task.issueType === 'DEFECT') { <span class="type-tag">Defect</span> }</strong><span class="task-name">{{ task.taskName }}</span>@if (task.milestone) { <span class="task-name">Milestone · {{ task.milestone }}</span> }</td><td>{{ task.assignee || 'Unassigned' }}</td><td><span [class]="'priority-tag ' + task.priority.toLowerCase()">{{ titleCase(task.priority) }}</span></td><td><span [class]="'status-tag ' + task.status.toLowerCase()">{{ titleCase(task.status) }}</span></td><td><span [class.overdue-date]="task.overdue">{{ formatDate(task.dueDate) }}</span></td><td class="blocker-cell">{{ task.blockerText || '—' }}</td></tr>
              }
            </tbody></table></div>
          }
          <div class="pagination"><span>{{ result.totalElements ? (result.page * result.size + 1) : 0 }}–{{ Math.min((result.page + 1) * result.size, result.totalElements) }} of {{ result.totalElements }} tasks</span><div><button class="page-button" type="button" [disabled]="result.page <= 0" (click)="changePage(result.page - 1)">Previous</button><span>Page {{ result.page + 1 }} of {{ Math.max(result.totalPages, 1) }}</span><button class="page-button" type="button" [disabled]="result.page + 1 >= result.totalPages" (click)="changePage(result.page + 1)">Next</button></div></div>
        }
      </section>

      <section class="surface risk-surface"><div class="section-heading"><div><div class="eyebrow">DETERMINISTIC FINDINGS</div><h2>Project risks</h2></div><span class="risk-count-label">{{ riskSummary()?.activeRiskCount ?? 0 }} active</span></div>
        @if (riskError()) { <div class="table-error" role="alert">{{ riskError() }}</div> }
        @else if (riskLoading()) { <div class="state-line"><span class="spinner"></span>Loading risk findings…</div> }
        @else if (riskSummary(); as risks) { <app-risk-cards [risks]="risks.risks" /> }
        <div class="dependency-note"><lucide-icon [img]="AlertIcon" [size]="14" /><span>The API provides original blocker/dependency text on tasks and task IDs attached to findings. A structured dependency list is not currently available.</span></div>
      </section>
      <app-ai-insights-panel [projectId]="projectId" />
    }
  `,
  styles: [`
    :host{display:block}.back-link{display:inline-flex;align-items:center;gap:6px;margin-bottom:18px;color:#718077;font-size:10px;font-weight:650;text-decoration:none}.back-link:hover{color:#405846}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:18px;margin-bottom:20px}.eyebrow{color:#829087;font-size:9px;font-weight:800;letter-spacing:1.35px}h1{margin:7px 0 6px;color:#23332d;font-size:27px;font-weight:720}.heading-copy{margin:0;color:#77827a;font-size:12px}.button{display:inline-flex;min-height:36px;align-items:center;justify-content:center;gap:8px;padding:0 13px;border:1px solid transparent;border-radius:6px;font:inherit;font-size:11px;font-weight:700;cursor:pointer}.button:disabled{opacity:.55;cursor:wait}.button-secondary{border-color:#dfe5de;color:#3c5146;background:#fff}.button-primary{color:#1d3329;background:#c1ef74}.summary-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:10px}.summary-metric{display:grid;min-height:93px;align-content:start;gap:8px;padding:13px;border:1px solid #e5e9e3;border-radius:6px;background:#fff}.summary-metric>span{color:#818d84;font-size:9px;font-weight:600}.summary-metric>strong{color:#2d4035;font-size:22px;line-height:1}.summary-metric small{color:#98a199;font-size:9px}.summary-metric .alert-value{color:#bd5746}.risk-summary{gap:7px}.severity-inline{display:flex;flex-wrap:wrap;gap:4px}.severity-pill{padding:3px 5px;border-radius:3px;color:#587149;background:#eff4e9;font-size:8px;font-weight:700}.severity-pill.critical{color:#b44f41;background:#faeae6}.severity-pill.high{color:#a76d39;background:#fbf1e4}.severity-pill.medium{color:#8a782d;background:#f8f4df}.notice{margin-top:12px;padding:10px 12px;border:1px solid #dfe9d5;border-radius:5px;color:#526f49;background:#f6faef;font-size:10px}.surface{border:1px solid #e5e9e3;border-radius:7px;background:#fff}.task-surface,.risk-surface,.milestone-surface{margin-top:14px;padding:17px}.milestone-surface table{min-width:560px;margin-top:10px}.type-tag{margin-left:6px;padding:2px 5px;border-radius:3px;color:#b34d40;background:#faeae6;font-size:8px;font-weight:700;vertical-align:middle}.milestone-tag{display:inline-block;padding:4px 6px;border-radius:3px;color:#5e704f;background:#eff4e9;font-size:8px;font-weight:700;white-space:nowrap}.milestone-tag.missed{color:#b34d40;background:#faeae6}.milestone-tag.at_risk{color:#a76d39;background:#fbf1e4}.milestone-tag.no_date{color:#77827a;background:#f0f2ef}.milestone-tag.complete{color:#52754b;background:#edf5e9}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-heading h2{margin:5px 0 0;color:#293a32;font-size:15px}.last-analysis,.risk-count-label{color:#879189;font-size:9px}.filter-bar{display:flex;align-items:flex-end;gap:9px;flex-wrap:wrap;margin:16px 0 13px;padding:12px;border:1px solid #edf0ec;border-radius:5px;background:#fafbf9}.filter-bar label{display:grid;gap:5px}.filter-bar label>span{color:#859088;font-size:8px;font-weight:750;text-transform:uppercase;letter-spacing:.5px}.filter-bar select,.filter-bar input{height:32px;min-width:110px;padding:0 8px;border:1px solid #e0e6df;border-radius:4px;outline-color:#a7c28c;color:#45574c;background:#fff;font:inherit;font-size:10px}.assignee-filter input{width:120px}.sort-direction{display:flex;width:auto;height:32px;align-items:center;gap:5px;padding:0 7px;color:#66746a;font-size:8px}.apply-button{min-height:32px;margin-left:auto;padding-inline:14px;font-size:10px}.table-scroll{overflow-x:auto}table{width:100%;border-collapse:collapse;min-width:760px}th{padding:10px 9px;border-bottom:1px solid #e8ece7;color:#89938c;font-size:8px;font-weight:800;letter-spacing:.55px;text-align:left;text-transform:uppercase}td{padding:11px 9px;border-bottom:1px solid #f0f2ef;color:#59675e;font-size:10px;vertical-align:middle}td:first-child{min-width:175px}.task-id,.task-name{display:block}.task-id{color:#385440;font-size:10px}.task-name{margin-top:3px;color:#89938c;font-size:9px}.priority-tag,.status-tag{display:inline-block;padding:4px 6px;border-radius:3px;color:#5e704f;background:#eff4e9;font-size:8px;font-weight:700;white-space:nowrap}.priority-tag.critical{color:#b34d40;background:#faeae6}.priority-tag.high{color:#a76d39;background:#fbf1e4}.priority-tag.medium{color:#86732b;background:#f8f4df}.status-tag.blocked{color:#b34d40;background:#faeae6}.status-tag.done{color:#52754b;background:#edf5e9}.status-tag.in_progress{color:#587881;background:#eaf2f2}.overdue-date{color:#bd5746;font-weight:700}.blocker-cell{max-width:240px;color:#7b857d}.pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 4px 0;color:#89938c;font-size:9px}.pagination>div{display:flex;align-items:center;gap:11px}.page-button{min-height:28px;padding:0 9px;border:1px solid #e2e7e1;border-radius:4px;color:#506158;background:#fff;font:inherit;font-size:9px;cursor:pointer}.page-button:disabled{opacity:.4;cursor:not-allowed}.empty-table{padding:32px;text-align:center;color:#879189;font-size:11px}.risk-surface{padding-bottom:4px}.dependency-note{display:flex;gap:8px;align-items:flex-start;margin:12px 0 10px;padding:9px 10px;border-radius:4px;color:#7e785f;background:#fbf8ea;font-size:9px;line-height:1.5}.dependency-note lucide-icon{flex:none;color:#a48b47}.state-panel,.state-line{display:flex;min-height:150px;align-items:center;justify-content:center;gap:10px;color:#77827a;font-size:11px}.state-error{padding:24px;color:#a55041}.spinner{width:14px;height:14px;border:2px solid #dce5d8;border-top-color:#6d8a58;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:950px){.filter-bar{align-items:flex-end}.summary-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:650px){.page-heading{align-items:flex-start;flex-direction:column}.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.task-surface,.risk-surface{padding:13px}.filter-bar{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.filter-bar label,.filter-bar select,.filter-bar input{width:100%;min-width:0}.apply-button{margin:0}.pagination{align-items:flex-start;flex-direction:column}.last-analysis{max-width:130px;text-align:right}}
    .detail-health { margin:0 0 8px; }
  `],
})
export class ProjectDetailPage {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  protected projectId = '';
  readonly project = signal<ProjectResponse | null>(null);
  readonly riskSummary = signal<ProjectRiskSummary | null>(null);
  readonly taskPage = signal<TaskPageResponse | null>(null);
  readonly loadingProject = signal(true);
  readonly riskLoading = signal(true);
  readonly tasksLoading = signal(true);
  readonly analyzing = signal(false);
  readonly projectError = signal('');
  readonly riskError = signal('');
  readonly taskError = signal('');
  readonly analysisNotice = signal('');
  readonly pageIndex = signal(0);
  statusFilter = '';
  priorityFilter = '';
  assigneeFilter = '';
  overdueFilter = '';
  sortBy: TaskSortField = 'TASK_ID';
  sortDirection: TaskSortDirection = 'ASC';
  protected readonly statuses: TaskStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'DONE'];
  protected readonly priorities: Priority[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  protected readonly severities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;
  protected readonly BackIcon = ArrowLeft;
  protected readonly RefreshIcon = RefreshCw;
  protected readonly SortIcon = ArrowUpDown;
  protected readonly AlertIcon = TriangleAlert;
  protected readonly titleCase = titleCase;
  protected readonly formatDate = formatDate;
  protected readonly Math = Math;

  constructor() {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadProject(); this.loadRisks(); this.loadTasks();
  }

  riskTotal(project: ProjectResponse): number { return Object.values(project.risksBySeverity).reduce((sum, value) => sum + value, 0); }
  toggleSort(): void { this.sortDirection = this.sortDirection === 'ASC' ? 'DESC' : 'ASC'; this.pageIndex.set(0); this.loadTasks(); }
  changePage(page: number): void { this.pageIndex.set(page); this.loadTasks(); }

  analyze(): void {
    this.analyzing.set(true); this.analysisNotice.set('');
    this.api.analyzeProject(this.projectId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (summary) => { this.riskSummary.set(summary); this.analyzing.set(false); this.analysisNotice.set(`Analysis refreshed at ${formatDateTime(summary.analysisTimestamp)}.`); this.loadProject(); this.loadTasks(); },
      error: (error: unknown) => { this.analysisNotice.set(errorMessage(error)); this.analyzing.set(false); },
    });
  }
  loadTasks(): void {
    this.tasksLoading.set(true); this.taskError.set('');
    this.taskPage.set(null);
    const query: TaskQuery = { page: this.pageIndex(), size: 10, sortBy: this.sortBy, sortDirection: this.sortDirection };
    if (this.statusFilter) query.status = this.statusFilter as TaskStatus;
    if (this.priorityFilter) query.priority = this.priorityFilter as Priority;
    if (this.assigneeFilter.trim()) query.assignee = this.assigneeFilter.trim();
    if (this.overdueFilter !== '') query.overdue = this.overdueFilter === 'true';
    this.api.getTasks(this.projectId, query).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => { this.taskPage.set(data); this.tasksLoading.set(false); },
      error: (error: unknown) => { this.taskPage.set(null); this.taskError.set(errorMessage(error)); this.tasksLoading.set(false); },
    });
  }

  private loadProject(): void {
    this.loadingProject.set(true); this.projectError.set('');
    this.api.getProject(this.projectId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => { this.project.set(data); this.loadingProject.set(false); },
      error: (error: unknown) => { this.projectError.set(errorMessage(error)); this.loadingProject.set(false); },
    });
  }
  private loadRisks(): void {
    this.riskLoading.set(true); this.riskError.set('');
    this.api.getProjectRisks(this.projectId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => { this.riskSummary.set(data); this.riskLoading.set(false); },
      error: (error: unknown) => { this.riskError.set(errorMessage(error)); this.riskLoading.set(false); },
    });
  }
}