import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.service';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.page').then((module) => module.LoginPage) },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard.page').then((module) => module.DashboardPage) },
      { path: 'upload', canActivate: [roleGuard('ADMIN', 'MANAGER')], loadComponent: () => import('./pages/upload.page').then((module) => module.UploadPage) },
      { path: 'projects', loadComponent: () => import('./pages/projects.page').then((module) => module.ProjectsPage) },
      { path: 'projects/:id', loadComponent: () => import('./pages/project-detail.page').then((module) => module.ProjectDetailPage) },
      { path: 'risks', loadComponent: () => import('./pages/risks.page').then((module) => module.RisksPage) },
      { path: 'reports', loadComponent: () => import('./pages/reports.page').then((module) => module.ReportsPage) },
      { path: 'users', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/users.page').then((module) => module.UsersPage) },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
