import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DeliveryPulseApi } from './deliverypulse-api.service';
import { ImportSummary, TaskPageResponse } from './api.models';

describe('DeliveryPulseApi', () => {
  let api: DeliveryPulseApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(DeliveryPulseApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads AI status and posts insight generation for one project', () => {
    api.getAiStatus().subscribe();
    http.expectOne('/api/ai/status').flush({});
    api.generateInsights('project-7').subscribe();
    const request = http.expectOne('/api/projects/project-7/insights');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('loads portfolio and weekly report through the configured API base', () => {
    api.getPortfolio().subscribe();
    http.expectOne('/api/dashboard/portfolio').flush({});
    api.getWeeklyReport().subscribe();
    http.expectOne('/api/reports/weekly').flush({});
  });

  it('serializes task filters, date, pagination, and sorting', () => {
    api.getTasks('project-7', {
      page: 2,
      size: 15,
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      assignee: '  Mira  ',
      overdue: true,
      analysisDate: '2026-10-15',
      sortBy: 'DUE_DATE',
      sortDirection: 'DESC',
    }).subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/api/projects/project-7/tasks');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('15');
    expect(request.request.params.get('status')).toBe('IN_PROGRESS');
    expect(request.request.params.get('priority')).toBe('HIGH');
    expect(request.request.params.get('assignee')).toBe('Mira');
    expect(request.request.params.get('overdue')).toBe('true');
    expect(request.request.params.get('analysisDate')).toBe('2026-10-15');
    expect(request.request.params.get('sortBy')).toBe('DUE_DATE');
    expect(request.request.params.get('sortDirection')).toBe('DESC');
    request.flush({} satisfies Partial<TaskPageResponse>);
  });

  it('posts the multipart field to the extension-matched upload endpoint', () => {
    const file = new File(['Task ID,Task Name'], 'Ops.CSV', { type: 'text/csv' });
    api.upload(file).subscribe();
    const request = http.expectOne('/api/uploads/csv');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBe(true);
    const uploadedFile = (request.request.body as FormData).get('file') as File;
    expect(uploadedFile.name).toBe('Ops.CSV');
    expect(uploadedFile.size).toBe(file.size);
    request.flush({ projectCount: 1, importedTaskCount: 0 } satisfies Partial<ImportSummary>);
  });
});