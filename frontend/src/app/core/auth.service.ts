import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Observable, catchError, map, of, tap, throwError } from 'rxjs';
import { API_BASE_URL } from './api.config';

export type Role = 'ADMIN' | 'MANAGER' | 'VIEWER';

export interface SessionUser {
  id: string;
  username: string;
  displayName: string;
  role: Role;
  active: boolean;
  projectIds: string[];
}

interface StoredSession {
  token: string;
  expiresAt: string;
  user: SessionUser;
}

const STORAGE_KEY = 'deliverypulse.session';

/**
 * The signed-in session. The bearer token is kept in sessionStorage, so it survives a reload but not closing
 * the tab; storage access is wrapped because it can throw in private windows.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<StoredSession | null>(readStored());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  /** Admins and Managers can upload, analyze and generate insights. */
  readonly canEdit = computed(() => this.user()?.role === 'ADMIN' || this.user()?.role === 'MANAGER');

  token(): string | null {
    const session = this.session();
    if (!session) return null;
    if (new Date(session.expiresAt).getTime() <= Date.now()) { this.clear(); return null; }
    return session.token;
  }

  login(username: string, password: string): Observable<SessionUser> {
    return this.http.post<StoredSession>(`${API_BASE_URL}/auth/login`, { username, password }).pipe(
      tap((session) => this.store(session)),
      map((session) => session.user),
    );
  }

  logout(): void {
    const request = this.token() ? this.http.post<void>(`${API_BASE_URL}/auth/logout`, null) : of(undefined);
    request.pipe(catchError(() => of(undefined))).subscribe(() => {
      this.clear();
      void this.router.navigate(['/login']);
    });
  }

  /** Test and session-restore hook; also used after an admin edits their own account. */
  setSession(session: StoredSession | null): void {
    if (session) this.store(session); else this.clear();
  }

  clear(): void {
    this.session.set(null);
    try { sessionStorage.removeItem(STORAGE_KEY); } catch { /* storage unavailable */ }
  }

  private store(session: StoredSession): void {
    this.session.set(session);
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session)); } catch { /* storage unavailable */ }
  }
}

function readStored(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const session = raw ? (JSON.parse(raw) as StoredSession) : null;
    return session && new Date(session.expiresAt).getTime() > Date.now() ? session : null;
  } catch {
    return null;
  }
}

/** Adds the bearer token to API calls; a 401 on any call except login ends the session. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const isApi = request.url.startsWith(API_BASE_URL);
  const authorized = token && isApi ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  return next(authorized).pipe(catchError((error: unknown) => {
    const status = (error as { status?: number }).status;
    if (status === 401 && isApi && !request.url.endsWith('/auth/login')) {
      auth.clear();
      void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
    }
    return throwError(() => error);
  }));
};

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.token() ? true : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const role = inject(AuthService).user()?.role;
    return role && roles.includes(role) ? true : inject(Router).createUrlTree(['/dashboard']);
  };
}
