import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', loadComponent: () => import('./pages/dashboard.page').then((module) => module.DashboardPage) },
  { path: 'upload', loadComponent: () => import('./pages/upload.page').then((module) => module.UploadPage) },
  { path: 'projects', loadComponent: () => import('./pages/projects.page').then((module) => module.ProjectsPage) },
  { path: 'projects/:id', loadComponent: () => import('./pages/project-detail.page').then((module) => module.ProjectDetailPage) },
  { path: 'risks', loadComponent: () => import('./pages/risks.page').then((module) => module.RisksPage) },
  { path: 'reports', loadComponent: () => import('./pages/reports.page').then((module) => module.ReportsPage) },
  { path: '**', redirectTo: 'dashboard' },
];