import { TestBed } from '@angular/core/testing';
import { AuthService, Role } from './auth.service';

/** Spec helper: sign in as a user of the given role without calling the API. */
export function signInAs(role: Role = 'ADMIN'): void {
  TestBed.inject(AuthService).setSession({
    token: 'test-token',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 'u1', username: role.toLowerCase(), displayName: `Test ${role}`, role, active: true, projectIds: [] },
  });
}
