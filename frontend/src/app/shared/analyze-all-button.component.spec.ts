import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AnalyzeAllButtonComponent } from './analyze-all-button.component';

describe('AnalyzeAllButtonComponent', () => {
  let http: HttpTestingController;

  function render(projectIds: string[]) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AnalyzeAllButtonComponent);
    fixture.componentRef.setInput('projectIds', projectIds);
    let finished = 0;
    fixture.componentInstance.finished.subscribe(() => finished++);
    fixture.detectChanges();
    return { fixture, finishedCount: () => finished };
  }

  const text = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.textContent ?? '';

  afterEach(() => http.verify());

  it('analyzes projects one at a time with progress, then reports completion', () => {
    const { fixture, finishedCount } = render(['p-1', 'p-2']);
    fixture.componentInstance.run();
    fixture.detectChanges();
    expect(text(fixture)).toContain('Analyzing 0 of 2');

    http.expectOne({ method: 'POST', url: '/api/projects/p-1/analyze' }).flush({});
    fixture.detectChanges();
    expect(text(fixture)).toContain('Analyzing 1 of 2');
    // The second project starts only after the first has finished.
    http.expectOne({ method: 'POST', url: '/api/projects/p-2/analyze' }).flush({});
    fixture.detectChanges();

    expect(text(fixture)).toContain('Analyze all projects');
    expect(finishedCount()).toBe(1);
  });

  it('stops on the first failure and says how far it got', () => {
    const { fixture, finishedCount } = render(['p-1', 'p-2', 'p-3']);
    fixture.componentInstance.run();
    http.expectOne('/api/projects/p-1/analyze').flush({});
    http.expectOne('/api/projects/p-2/analyze').flush({ detail: 'Project not found' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text(fixture)).toContain('Stopped after 1 of 3 projects: Project not found');
    expect(finishedCount()).toBe(1);
  });

  it('is disabled when there are no projects', () => {
    const { fixture } = render([]);
    expect((fixture.nativeElement as HTMLElement).querySelector('button')?.disabled).toBe(true);
  });
});
