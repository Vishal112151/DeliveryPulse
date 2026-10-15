import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideArrowUpRight as ArrowUpRight, LucideFolderKanban as FolderKanban, LucideSearch as Search } from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProjectResponse } from '../core/api.models';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { LucideIconCompatComponent } from '../shared/lucide-icon-compat.component';
import { errorMessage } from '../shared/view-helpers';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LucideIconCompatComponent],
  template: `
    <section class="page-heading"><div><div class="eyebrow">PROJECT REGISTER</div><h1>Projects</h1><p class="heading-copy">Browse active delivery work and its latest risk profile.</p></div><div class="project-total">{{ projects().length }} projects</div></section>
    <section class="surface list-surface">
      <div class="list-toolbar"><label class="search-box"><lucide-icon [img]="SearchIcon" [size]="16" /><input type="search" [(ngModel)]="searchTerm" aria-label="Search projects" placeholder="Search by project or worksheet" /></label><span class="result-count">{{ filteredProjects().length }} results</span></div>
      @if (loading()) { <div class="state-line"><span class="spinner"></span>Loading projects…</div> }
      @else if (error()) { <div class="state-error" role="alert"><strong>Projects unavailable</strong><span>{{ error() }}</span><button class="text-button" (click)="load()">Retry</button></div> }
      @else if (filteredProjects().length === 0) { <div class="empty-state"><div class="empty-icon"><lucide-icon [img]="FolderIcon" [size]="19" /></div><strong>{{ projects().length ? 'No matching projects' : 'No projects imported' }}</strong><span>{{ projects().length ? 'Try a different search term.' : 'Upload a workbook or CSV to begin.' }}</span><a *ngIf="!projects().length" routerLink="/upload" class="button-link">Upload data</a></div> }
      @else {
        <div class="project-list">
          @for (project of filteredProjects(); track project.id) {
            <a class="project-row" [routerLink]="['/projects', project.id]">
              <div class="project-symbol">{{ project.name.slice(0, 1) }}</div>
              <div class="project-name"><strong>{{ project.name }}</strong><span>{{ project.sourceSheetName || 'Project' }}{{ project.description ? ' · ' + project.description : '' }}</span></div>
              <div class="project-stat"><strong>{{ project.taskCount }}</strong><span>tasks</span></div>
              <div class="project-stat"><strong>{{ project.completedTaskCount }}<small>/{{ project.taskCount }}</small></strong><span>complete</span></div>
              <div class="project-stat risk-stat" [class.has-risk]="riskTotal(project) > 0"><strong>{{ riskTotal(project) }}</strong><span>active risks</span></div>
              <div class="risk-mini">@for (severity of severities; track severity) { @if (project.risksBySeverity[severity]) { <span [class]="'severity-bar ' + severity.toLowerCase()" [title]="severity + ': ' + project.risksBySeverity[severity]"></span> } }</div>
              <span class="updated">{{ project.analysisTimestamp ? ('Analyzed ' + (project.analysisTimestamp | date:'MMM d, h:mm a')) : 'Not analyzed' }}</span>
              <lucide-icon class="open-icon" [img]="ArrowIcon" [size]="15" />
            </a>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    :host{display:block}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:24px}.eyebrow{color:#829087;font-size:9px;font-weight:800;letter-spacing:1.35px}h1{margin:7px 0 6px;color:#23332d;font-size:27px;font-weight:720}.heading-copy{margin:0;color:#77827a;font-size:12px}.project-total{color:#7a877d;font-size:11px}.surface{overflow:hidden;border:1px solid #e5e9e3;border-radius:7px;background:#fff}.list-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 17px;border-bottom:1px solid #edf0ec}.search-box{display:flex;width:min(370px,100%);height:35px;align-items:center;gap:9px;padding:0 10px;border:1px solid #e2e7e1;border-radius:5px;color:#829087}.search-box input{width:100%;border:0;outline:0;color:#34443a;background:transparent;font:inherit;font-size:11px}.search-box input::placeholder{color:#a0a9a1}.result-count{color:#8a948c;font-size:10px}.project-row{display:grid;grid-template-columns:34px minmax(130px,1.5fr) 56px 72px 75px 34px 125px 18px;align-items:center;gap:14px;padding:14px 17px;border-bottom:1px solid #eff1ee;color:inherit;text-decoration:none;transition:background 120ms ease}.project-row:hover{background:#fafbf8}.project-symbol{display:grid;width:32px;height:32px;place-items:center;border-radius:7px;color:#51694a;background:#eef3e9;font-size:13px;font-weight:750}.project-name{display:grid;min-width:0;gap:4px}.project-name strong{overflow:hidden;color:#34443a;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.project-name span{overflow:hidden;color:#929a94;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.project-stat{display:grid;gap:3px}.project-stat strong{color:#46594e;font-size:12px;font-weight:700}.project-stat strong small{color:#9aa39c;font-size:9px;font-weight:500}.project-stat span{color:#929b94;font-size:9px}.risk-stat.has-risk strong{color:#c16a4e}.risk-mini{display:flex;justify-content:center;gap:3px}.severity-bar{width:5px;height:16px;border-radius:2px;background:#a8c68c}.severity-bar.critical{background:#d95a48}.severity-bar.high{background:#e99b45}.severity-bar.medium{background:#e6c75b}.updated{color:#8d978f;font-size:9px}.open-icon{color:#98a39a}.state-line,.state-error{display:flex;align-items:center;gap:10px;padding:35px 18px;color:#77827a;font-size:11px}.state-error{align-items:flex-start;flex-direction:column;color:#a55041}.state-error span{color:#82746e}.text-button{padding:0;border:0;color:#54754a;background:none;font:inherit;font-size:10px;font-weight:700;cursor:pointer}.spinner{width:14px;height:14px;border:2px solid #dce5d8;border-top-color:#6d8a58;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.empty-state{display:flex;min-height:230px;align-items:center;justify-content:center;flex-direction:column;gap:8px;color:#7d8980}.empty-state strong{margin-top:5px;color:#3b4c42;font-size:13px}.empty-state>span{font-size:10px}.empty-icon{display:grid;width:40px;height:40px;place-items:center;border-radius:50%;color:#617e4e;background:#eef4e8}.button-link{margin-top:7px;padding:9px 12px;border-radius:5px;color:#1d3329;background:#c1ef74;font-size:10px;font-weight:700;text-decoration:none}
    @media(max-width:1000px){.project-row{grid-template-columns:34px minmax(130px,1fr) 52px 68px 70px 30px 17px}.updated{display:none}}@media(max-width:650px){.project-row{grid-template-columns:32px minmax(0,1fr) 45px 56px 16px;gap:9px;padding:12px}.project-row .project-stat:nth-of-type(4),.risk-mini{display:none}.project-name span{max-width:180px}.project-total{display:none}.list-toolbar{padding:12px}.project-stat span{font-size:8px}}
  `],
})
export class ProjectsPage {
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly projects = signal<ProjectResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  searchTerm = '';
  protected readonly SearchIcon = Search;
  protected readonly ArrowIcon = ArrowUpRight;
  protected readonly FolderIcon = FolderKanban;
  protected readonly severities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;

  constructor() { this.load(); }
  filteredProjects(): ProjectResponse[] {
    const term = this.searchTerm.trim().toLowerCase();
    return this.projects().filter((project) => !term || `${project.name} ${project.sourceSheetName ?? ''}`.toLowerCase().includes(term));
  }
  riskTotal(project: ProjectResponse): number { return Object.values(project.risksBySeverity).reduce((sum, count) => sum + count, 0); }
  load(): void {
    this.loading.set(true); this.error.set('');
    this.api.getProjects().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (projects) => { this.projects.set(projects); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(errorMessage(error)); this.loading.set(false); },
    });
  }
}