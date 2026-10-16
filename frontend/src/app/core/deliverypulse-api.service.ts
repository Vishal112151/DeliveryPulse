import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, concatMap, from } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  AiStatusResponse,
  ImportSummary,
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

  upload(file: File): Observable<ImportSummary> {
    const body = new FormData();
    body.append('file', file, file.name);
    const endpoint = /\.csv$/i.test(file.name) ? 'csv' : 'excel';
    return this.http.post<ImportSummary>(`${this.baseUrl}/uploads/${endpoint}`, body);
  }
}