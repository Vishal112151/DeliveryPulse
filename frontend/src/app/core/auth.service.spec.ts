import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LoginPage } from '../pages/login.page';
import { AuthService, authGuard, authInterceptor, roleGuard } from './auth.service';
import { signInAs } from './testing';

describe('Authentication', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the bearer token on API calls only', () => {
    signInAs('MANAGER');
    const client = TestBed.inject(HttpClient);
    client.get('/api/projects').subscribe();
    client.get('/actuator/health').subscribe();

    expect(http.expectOne('/api/projects').request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(http.expectOne('/actuator/health').request.headers.has('Authorization')).toBe(false);
  });

  it('ends the session and goes to the login page when the API answers 401', () => {
    signInAs('VIEWER');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    TestBed.inject(HttpClient).get('/api/projects').subscribe({ error: () => undefined });
    http.expectOne('/api/projects').flush({ detail: 'Sign in' }, { status: 401, statusText: 'Unauthorized' });

    expect(TestBed.inject(AuthService).user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('guards routes by sign-in and role', () => {
    const run = (guard: typeof authGuard) =>
      TestBed.runInInjectionContext(() => guard({} as never, { url: '/users' } as never));

    expect(String(run(authGuard))).toBe('/login?returnUrl=%2Fusers');
    signInAs('MANAGER');
    expect(run(authGuard)).toBe(true);
    expect(String(run(roleGuard('ADMIN')))).toBe('/dashboard');
    expect(run(roleGuard('ADMIN', 'MANAGER'))).toBe(true);
  });

  it('login page signs in, stores the session and opens the requested page', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.componentInstance.username = 'maria';
    fixture.componentInstance.password = 'password-123';
    fixture.componentInstance.submit();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ username: 'maria', password: 'password-123' });
    request.flush({ token: 'abc', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { id: 'u2', username: 'maria', displayName: 'Maria', role: 'MANAGER', active: true, projectIds: [] } });

    expect(TestBed.inject(AuthService).user()?.role).toBe('MANAGER');
    expect(TestBed.inject(AuthService).token()).toBe('abc');
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('login page shows the API error and clears the password', () => {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.componentInstance.username = 'maria';
    fixture.componentInstance.password = 'wrong';
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush({ detail: 'Wrong username or password.' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Wrong username or password.');
    expect(fixture.componentInstance.password).toBe('');
  });
});
