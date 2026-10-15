import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { PortfolioRiskSummary, ProjectRiskSummary, RiskResponse } from '../core/api.models';
import { DashboardPage } from './dashboard.page';
import { ProjectDetailPage } from './project-detail.page';
import { ProjectsPage } from './projects.page';
import { ReportsPage } from './reports.page';
import { RisksPage } from './risks.page';
import { UploadPage } from './upload.page';

const severities = (critical = 0, high = 0, medium = 0, low = 0) => ({ CRITICAL: critical, HIGH: high, MEDIUM: medium, LOW: low });
const categories = { OVERDUE_TASK: 0, BLOCKED_TASK: 0, CRITICAL_TASK_AT_RISK: 0, DEPENDENCY_RISK: 0, DELIVERY_RISK: 0, DATA_QUALITY_WARNING: 0 };

function risk(id: string, severity: RiskResponse['severity'], title: string): RiskResponse {
  return { id, category: 'BLOCKED_TASK', severity, certainty: 'CONFIRMED', status: 'OPEN', title,
    explanation: `${title} explanation`, evidence: 'Task ID: EC-102', supportingTaskIds: ['EC-102'],
    recommendedAction: 'Escalate.', detectedAt: '2026-10-15T08:00:00Z' };
}

function project(name: string, analyzed: boolean, risks: RiskResponse[] = []): ProjectRiskSummary {
  return { projectId: `id-${name}`, projectName: name, analysisDate: '2026-10-15',
    analysisTimestamp: analyzed ? '2026-10-15T08:00:00Z' : null, taskCount: 8, completedTaskCount: 2,
    incompleteTaskCount: 6, overdueTaskCount: 2, blockedTaskCount: 1, activeRiskCount: risks.length,
    risksBySeverity: severities(risks.filter((r) => r.severity === 'CRITICAL').length, risks.filter((r) => r.severity === 'HIGH').length),
    risksByCategory: categories, risks };
}

function portfolio(projects: ProjectRiskSummary[]): PortfolioRiskSummary {
  const sum = (key: 'taskCount' | 'overdueTaskCount' | 'blockedTaskCount' | 'activeRiskCount') =>
    projects.reduce((total, item) => total + item[key], 0);
  return { analysisDate: '2026-10-15', analysisTimestamp: '2026-10-15T08:00:00Z', projectCount: projects.length,
    taskCount: sum('taskCount'), completedTaskCount: 2 * projects.length, incompleteTaskCount: 6 * projects.length,
    overdueTaskCount: sum('overdueTaskCount'), blockedTaskCount: sum('blockedTaskCount'),
    activeRiskCount: sum('activeRiskCount'), risksBySeverity: severities(1, 1), risksByCategory: categories, projects };
}

describe('Pages against the API', () => {
  let http: HttpTestingController;

  function render<T>(page: Type<T>, extraProviders: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), ...(extraProviders as never[])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(page);
    fixture.detectChanges();
    return fixture;
  }

  const text = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.textContent ?? '';

  afterEach(() => http.verify());

  it('dashboard shows backend metrics and flags projects not analyzed today', () => {
    const fixture = render(DashboardPage);
    http.expectOne('/api/dashboard/portfolio').flush(portfolio([
      project('ECommerce', true, [risk('r1', 'CRITICAL', 'Critical-priority task at risk: EC-102')]),
      project('CRM', false),
    ]));
    fixture.detectChanges();

    expect(text(fixture)).toContain('16');
    expect(text(fixture)).toContain('Not analyzed on Oct 15, 2026: CRM.');
    expect(text(fixture)).toContain('as of Oct 15, 2026');
  });

  it('dashboard shows the API error detail instead of empty metrics', () => {
    const fixture = render(DashboardPage);
    http.expectOne('/api/dashboard/portfolio').flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(text(fixture)).toContain('Portfolio unavailable');
    expect(text(fixture)).toContain('Database unavailable');
  });

  it('upload shows row-level validation errors from a 422 and says nothing was imported', () => {
    const fixture = render(UploadPage);
    const file = new File(['Task ID,Task Name\n'], 'Broken.csv', { type: 'text/csv' });
    fixture.componentInstance.onFileInput({ target: { files: { item: () => file } } } as unknown as Event);
    fixture.componentInstance.upload();
    http.expectOne('/api/uploads/csv').flush({
      projectCount: 0, importedTaskCount: 0, importedProjects: [], skippedWorksheets: [], warnings: [],
      errors: [{ worksheet: 'Broken', row: 2, column: 'Priority', code: 'INVALID_PRIORITY', message: 'Priority must be Low, Medium, High, or Critical.' }],
    }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(text(fixture)).toContain('IMPORT REJECTED');
    expect(text(fixture)).toContain('Broken · row 2 · Priority');
    expect(text(fixture)).toContain('No records were imported');
  });

  it('projects page shows the empty state with an upload link', () => {
    const fixture = render(ProjectsPage);
    http.expectOne('/api/projects').flush([]);
    fixture.detectChanges();

    expect(text(fixture)).toContain('No projects imported');
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/upload"]')).toBeTruthy();
  });

  it('risks page filters findings by severity', async () => {
    const fixture = render(RisksPage);
    http.expectOne('/api/dashboard/portfolio').flush(portfolio([
      project('ECommerce', true, [risk('r1', 'CRITICAL', 'Critical one'), risk('r2', 'HIGH', 'High one')]),
    ]));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(text(fixture)).toContain('2 findings');

    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('select[aria-label="Filter by severity"]')!;
    select.value = 'CRITICAL';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(text(fixture)).toContain('1 findings');
    expect(text(fixture)).toContain('Critical one');
    expect(text(fixture)).not.toContain('High one');
  });

  it('reports page shows the weekly window and per-project totals', () => {
    const fixture = render(ReportsPage);
    http.expectOne('/api/reports/weekly').flush({ ...portfolio([project('CRM', true)]),
      weekStartDate: '2026-10-09', weekEndDate: '2026-10-15' });
    fixture.detectChanges();

    expect(text(fixture)).toContain('Oct 9, 2026 — Oct 15, 2026');
    expect(text(fixture)).toContain('CRM');
  });

  it('project detail renders tasks and risk evidence returned by the API', () => {
    const fixture = render(ProjectDetailPage, [
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p-1' }) } } },
    ]);
    const summary = project('ECommerce', true, [risk('r1', 'CRITICAL', 'Critical-priority task at risk: EC-102')]);
    http.expectOne('/api/projects/p-1').flush({ id: 'p-1', name: 'ECommerce', sourceSheetName: 'ECommerce',
      description: null, createdAt: '', updatedAt: '', analysisDate: '2026-10-15', analysisTimestamp: '2026-10-15T08:00:00Z',
      taskCount: 1, completedTaskCount: 0, incompleteTaskCount: 1, overdueTaskCount: 1, blockedTaskCount: 1,
      risksBySeverity: severities(1) });
    http.expectOne('/api/projects/p-1/risks').flush(summary);
    http.expectOne((request) => request.url === '/api/projects/p-1/tasks').flush({
      content: [{ id: 't1', projectId: 'p-1', taskId: 'EC-102', taskName: 'Payment Gateway Integration',
        teamOrModule: 'Backend', assignee: 'Meera', priority: 'CRITICAL', status: 'BLOCKED', startDate: '2026-10-09',
        dueDate: '2026-10-13', overdue: true, blockerText: 'Vendor sandbox credentials pending', description: null }],
      page: 0, size: 10, totalElements: 1, totalPages: 1, analysisDate: '2026-10-15',
      analysisTimestamp: '2026-10-15T08:00:00Z', sortBy: 'TASK_ID', sortDirection: 'ASC' });
    fixture.detectChanges();
    http.expectOne('/api/ai/status').flush({ available: false, model: 'm', provider: 'anthropic', message: '' });
    fixture.detectChanges();

    expect(text(fixture)).toContain('Payment Gateway Integration');
    expect(text(fixture)).toContain('Vendor sandbox credentials pending');
    expect(text(fixture)).toContain('Critical-priority task at risk: EC-102');
    expect(text(fixture)).toContain('Task ID: EC-102');
  });
});
