import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, concatMap, from } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  AiStatusResponse,
  ImportSummary,
  JiraStatusResponse,
  JobRun,
  JobStatus,
  ReportSnapshotSummary,
  PortfolioRiskSummary,
  ProjectInsightsResponse,
  ProjectResponse,
  ProjectRiskSummary,
  TaskPageResponse,
  TaskQuery,
  WeeklyReportResponse,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class DeliveryPulseApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = API_BASE_URL;

  getPortfolio(): Observable<PortfolioRiskSummary> {
    return this.http.get<PortfolioRiskSummary>(`${this.baseUrl}/dashboard/portfolio`);
  }

  getProjects(): Observable<ProjectResponse[]> {
    return this.http.get<ProjectResponse[]>(`${this.baseUrl}/projects`);
  }

  getProject(projectId: string): Observable<ProjectResponse> {
    return this.http.get<ProjectResponse>(`${this.baseUrl}/projects/${projectId}`);
  }

  getTasks(projectId: string, query: TaskQuery): Observable<TaskPageResponse> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortBy', query.sortBy)
      .set('sortDirection', query.sortDirection);
    if (query.status) params = params.set('status', query.status);
    if (query.priority) params = params.set('priority', query.priority);
    if (query.assignee?.trim()) params = params.set('assignee', query.assignee.trim());
    if (query.overdue !== undefined) params = params.set('overdue', query.overdue);
    if (query.analysisDate) params = params.set('analysisDate', query.analysisDate);
    return this.http.get<TaskPageResponse>(`${this.baseUrl}/projects/${projectId}/tasks`, { params });
  }

  analyzeProject(projectId: string, analysisDate?: string): Observable<ProjectRiskSummary> {
    let params = new HttpParams();
    if (analysisDate) params = params.set('analysisDate', analysisDate);
    return this.http.post<ProjectRiskSummary>(`${this.baseUrl}/projects/${projectId}/analyze`, null, { params });
  }

  /** Analyzes the projects one after another; emits each project's result as it finishes. */
  analyzeAll(projectIds: string[]): Observable<ProjectRiskSummary> {
    return from(projectIds).pipe(concatMap((projectId) => this.analyzeProject(projectId)));
  }

  getProjectRisks(projectId: string): Observable<ProjectRiskSummary> {
    return this.http.get<ProjectRiskSummary>(`${this.baseUrl}/projects/${projectId}/risks`);
  }

  getWeeklyReport(): Observable<WeeklyReportResponse> {
    return this.http.get<WeeklyReportResponse>(`${this.baseUrl}/reports/weekly`);
  }

  getAiStatus(): Observable<AiStatusResponse> {
    return this.http.get<AiStatusResponse>(`${this.baseUrl}/ai/status`);
  }

  generateInsights(projectId: string): Observable<ProjectInsightsResponse> {
    return this.http.post<ProjectInsightsResponse>(`${this.baseUrl}/projects/${projectId}/insights`, null);
  }

  generatePortfolioInsights(): Observable<ProjectInsightsResponse> {
    return this.http.post<ProjectInsightsResponse>(`${this.baseUrl}/portfolio/insights`, null);
  }

  getJobs(): Observable<JobStatus[]> {
    return this.http.get<JobStatus[]>(`${this.baseUrl}/jobs`);
  }

  runJob(name: JobStatus['name']): Observable<JobRun> {
    return this.http.post<JobRun>(`${this.baseUrl}/jobs/${name}/run`, null);
  }

  getReportSnapshots(): Observable<ReportSnapshotSummary[]> {
    return this.http.get<ReportSnapshotSummary[]>(`${this.baseUrl}/reports/weekly/snapshots`);
  }

  getReportSnapshot(id: string): Observable<WeeklyReportResponse> {
    return this.http.get<WeeklyReportResponse>(`${this.baseUrl}/reports/weekly/snapshots/${id}`);
  }

  getJiraStatus(): Observable<JiraStatusResponse> {
    return this.http.get<JiraStatusResponse>(`${this.baseUrl}/integrations/jira/status`);
  }

  /** Imports the configured Jira projects as an update; the response has the same shape as an upload. */
  syncJira(): Observable<ImportSummary> {
    return this.http.post<ImportSummary>(`${this.baseUrl}/integrations/jira/sync`, null);
  }

  /** With updateExisting, projects that already exist are updated by Task ID instead of rejected. */
  upload(file: File, updateExisting = false): Observable<ImportSummary> {
    const body = new FormData();
    body.append('file', file, file.name);
    const endpoint = /\.csv$/i.test(file.name) ? 'csv' : 'excel';
    const params = updateExisting ? new HttpParams().set('update', true) : undefined;
    return this.http.post<ImportSummary>(`${this.baseUrl}/uploads/${endpoint}`, body, { params });
  }
}