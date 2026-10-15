import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AiStatusResponse, ProjectInsightsResponse } from '../core/api.models';
import { AiInsightsPanelComponent } from './ai-insights-panel.component';

const statement = (text: string, basis: 'FACT' | 'ASSUMPTION', ids: string[]) => ({ text, basis, supportingTaskIds: ids });

function insights(overrides: Partial<ProjectInsightsResponse>): ProjectInsightsResponse {
  return {
    projectId: 'p-1', projectName: 'Atlas', source: 'AI', status: 'GENERATED', message: 'ok',
    model: 'claude-opus-5-5', generatedAt: '2026-10-15T08:00:00Z', analysisDate: '2026-10-15',
    executiveSummary: [statement('AT-2 is overdue.', 'FACT', ['AT-2']), statement('Go-live may slip.', 'ASSUMPTION', ['AT-4'])],
    riskExplanations: [{ riskId: 'r-1', title: 'Critical-priority task at risk: AT-2', severity: 'CRITICAL', category: 'CRITICAL_TASK_AT_RISK',
      certainty: 'CONFIRMED', supportingTaskIds: ['AT-2'], explanation: [statement('Due 12 Oct.', 'FACT', ['AT-2'])] }],
    recommendedActions: [{ action: 'Agree a recovery date.', basis: 'ASSUMPTION', supportingTaskIds: ['AT-2'], relatedRiskIds: ['r-1'] }],
    weeklyStatus: { summary: [], progress: [statement('Discovery done.', 'FACT', ['AT-1'])], risksAndIssues: [], nextSteps: [] },
    validationWarnings: [], disclaimer: 'AI-generated draft.',
    ...overrides,
  };
}

describe('AiInsightsPanelComponent', () => {
  let http: HttpTestingController;

  function render(status: Partial<AiStatusResponse>) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AiInsightsPanelComponent);
    fixture.componentRef.setInput('projectId', 'p-1');
    fixture.detectChanges();
    http.expectOne('/api/ai/status').flush({ available: false, model: 'claude-opus-5-5', provider: 'anthropic', message: '', ...status });
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('offers a local summary and makes no generation call until asked when AI is off', () => {
    const fixture = render({ available: false });
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('AI off');
    expect(text).toContain('No data leaves this server');
    expect(text).toContain('Generate summary');
  });

  it('renders AI statements with fact/assumption labels, task IDs and deterministic severity', () => {
    const fixture = render({ available: true });
    fixture.componentInstance.generate();
    http.expectOne('/api/projects/p-1/insights').flush(insights({}));
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('AI-generated draft');
    expect(element.textContent).toContain('claude-opus-5-5');
    const bases = Array.from(element.querySelectorAll('.basis')).map((node) => node.textContent?.trim());
    expect(bases).toContain('Fact');
    expect(bases).toContain('Assumption');
    expect(element.querySelector('.severity.critical')?.textContent).toContain('Critical');
    expect(element.textContent).toContain('AT-2');
    expect(element.textContent).toContain('Draft weekly status report');
  });

  it('explains why the deterministic fallback was used after a provider failure', () => {
    const fixture = render({ available: true });
    fixture.componentInstance.generate();
    http.expectOne('/api/projects/p-1/insights').flush(insights({
      source: 'DETERMINISTIC_FALLBACK', status: 'RATE_LIMITED', model: null,
      message: 'The AI provider rate limit was reached. Showing the deterministic summary instead.',
      validationWarnings: [], disclaimer: 'No AI was used.',
    }));
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.source-line.degraded')).not.toBeNull();
    expect(element.textContent).toContain('AI provider rate limit reached');
    expect(element.textContent).toContain('Showing the deterministic summary instead');
  });

  it('shows an error and keeps the button usable when the request fails', () => {
    const fixture = render({ available: false });
    fixture.componentInstance.generate();
    http.expectOne('/api/projects/p-1/insights').flush({ detail: 'Project not found' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('Project not found');
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});
