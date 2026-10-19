import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { ProjectResponse } from '../core/api.models';
import { Role, SessionUser } from '../core/auth.service';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { errorMessage, titleCase } from '../shared/view-helpers';

interface Draft {
  id: string | null;
  username: string;
  displayName: string;
  role: Role;
  active: boolean;
  password: string;
  projectIds: string[];
}

/** Admin-only: create users, set their role and the projects a Manager or Viewer can see. */
@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="page-heading"><div><div class="eyebrow">ACCESS CONTROL</div><h1>Users</h1><p class="heading-copy">Admins see every project. Managers and Viewers see only the projects ticked for them.</p></div><button class="button button-primary" type="button" (click)="startNew()">New user</button></section>
    @if (error()) { <div class="state-error" role="alert">{{ error() }}</div> }
    <div class="users-layout">
      <section class="surface">
        @if (loading()) { <div class="state-line"><span class="spinner"></span>Loading users…</div> }
        @else {
          <table><thead><tr><th>User</th><th>Role</th><th>Projects</th><th>Status</th></tr></thead><tbody>
            @for (user of users(); track user.id) {
              <tr [class.selected]="draft()?.id === user.id" (click)="edit(user)"><td><strong>{{ user.displayName }}</strong><span>{{ user.username }}</span></td><td><span [class]="'role-tag ' + user.role.toLowerCase()">{{ titleCase(user.role) }}</span></td><td>{{ user.role === 'ADMIN' ? 'All' : user.projectIds.length }}</td><td>{{ user.active ? 'Active' : 'Disabled' }}</td></tr>
            }
          </tbody></table>
        }
      </section>
      @if (draft(); as form) {
        <form class="surface editor" (submit)="$event.preventDefault(); save(form)">
          <h2>{{ form.id ? 'Edit ' + form.username : 'New user' }}</h2>
          @if (!form.id) { <label><span>Username</span><input name="username" [(ngModel)]="form.username" required /></label> }
          <label><span>Display name</span><input name="displayName" [(ngModel)]="form.displayName" required /></label>
          <label><span>Role</span><select name="role" [(ngModel)]="form.role">@for (role of roles; track role) { <option [value]="role">{{ titleCase(role) }}</option> }</select></label>
          <label><span>{{ form.id ? 'New password (leave blank to keep)' : 'Password' }}</span><input name="password" type="password" autocomplete="new-password" [(ngModel)]="form.password" [required]="!form.id" minlength="8" /></label>
          @if (form.id) { <label class="check"><input type="checkbox" name="active" [(ngModel)]="form.active" /><span>Active (unticking signs the user out)</span></label> }
          @if (form.role !== 'ADMIN') {
            <fieldset><legend>Projects</legend>
              @for (project of projects(); track project.id) {
                <label class="check"><input type="checkbox" [checked]="form.projectIds.includes(project.id)" (change)="toggle(form, project.id)" /><span>{{ project.name }}</span></label>
              } @empty { <small>No projects yet.</small> }
            </fieldset>
          }
          @if (formError()) { <div class="form-error" role="alert">{{ formError() }}</div> }
          <div class="actions"><button class="button" type="button" (click)="draft.set(null)">Cancel</button><button class="button button-primary" type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save' }}</button></div>
        </form>
      }
    </div>
  `,
  styles: [`
    :host{display:block}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:15px;margin-bottom:20px}.eyebrow{color:#829087;font-size:9px;font-weight:800;letter-spacing:1.35px}h1{margin:7px 0 6px;color:#23332d;font-size:27px;font-weight:720}.heading-copy{margin:0;color:#77827a;font-size:12px}
    .users-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(280px,360px);align-items:start;gap:14px}.surface{padding:16px;border:1px solid #e5e9e3;border-radius:7px;background:#fff}
    table{width:100%;border-collapse:collapse}th{padding:9px 8px;border-bottom:1px solid #e7ebe6;color:#89938c;font-size:8px;text-align:left;text-transform:uppercase;letter-spacing:.45px}td{padding:10px 8px;border-bottom:1px solid #f0f2ef;color:#5e6b62;font-size:10px;cursor:pointer}td strong,td span{display:block}td span{margin-top:2px;color:#89938c;font-size:9px}tr.selected td{background:#f6faef}
    .role-tag{display:inline-block!important;padding:3px 6px;border-radius:3px;color:#5e704f!important;background:#eff4e9;font-size:8px!important;font-weight:700}.role-tag.admin{color:#b34d40!important;background:#faeae6}.role-tag.manager{color:#a76d39!important;background:#fbf1e4}
    .editor{display:grid;gap:11px}.editor h2{margin:0;color:#293a32;font-size:15px}label{display:grid;gap:5px}label>span{color:#66746a;font-size:9px;font-weight:750;text-transform:uppercase;letter-spacing:.5px}input,select{height:34px;padding:0 8px;border:1px solid #dfe5de;border-radius:5px;font:inherit;font-size:12px}
    .check{display:flex;align-items:center;gap:8px}.check input{height:auto}.check span{color:#4f5f55;font-size:11px;font-weight:500;text-transform:none;letter-spacing:0}
    fieldset{display:grid;gap:7px;max-height:220px;overflow:auto;margin:0;padding:10px;border:1px solid #edf0ec;border-radius:5px}legend{color:#66746a;font-size:9px;font-weight:750;text-transform:uppercase}small{color:#89938c;font-size:10px}
    .actions{display:flex;justify-content:flex-end;gap:8px}.button{display:inline-flex;min-height:34px;align-items:center;padding:0 13px;border:1px solid #dfe5de;border-radius:6px;color:#3c5146;background:#fff;font:inherit;font-size:11px;font-weight:700;cursor:pointer}.button-primary{border-color:transparent;color:#1d3329;background:#c1ef74}.button:disabled{opacity:.55}
    .state-error,.form-error{padding:10px;border-radius:5px;color:#ad5142;background:#fff4f0;font-size:11px}.state-error{margin-bottom:12px}.state-line{display:flex;gap:10px;align-items:center;justify-content:center;min-height:120px;color:#77827a;font-size:11px}
    .spinner{width:13px;height:13px;border:2px solid #dce5d8;border-top-color:#6d8a58;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:900px){.users-layout{grid-template-columns:1fr}}
  `],
})
export class UsersPage {
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly users = signal<SessionUser[]>([]);
  readonly projects = signal<ProjectResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly draft = signal<Draft | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  protected readonly roles: Role[] = ['ADMIN', 'MANAGER', 'VIEWER'];
  protected readonly titleCase = titleCase;

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    forkJoin([this.api.listUsers(), this.api.getProjects()]).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ([users, projects]) => { this.users.set(users); this.projects.set(projects); this.loading.set(false); },
      error: (error: unknown) => { this.error.set(errorMessage(error)); this.loading.set(false); },
    });
  }

  startNew(): void {
    this.formError.set('');
    this.draft.set({ id: null, username: '', displayName: '', role: 'VIEWER', active: true, password: '', projectIds: [] });
  }

  edit(user: SessionUser): void {
    this.formError.set('');
    this.draft.set({ id: user.id, username: user.username, displayName: user.displayName, role: user.role,
      active: user.active, password: '', projectIds: [...user.projectIds] });
  }

  toggle(form: Draft, projectId: string): void {
    form.projectIds = form.projectIds.includes(projectId)
      ? form.projectIds.filter((id) => id !== projectId) : [...form.projectIds, projectId];
  }

  save(form: Draft): void {
    this.saving.set(true); this.formError.set('');
    const common = { displayName: form.displayName.trim(), role: form.role,
      projectIds: form.role === 'ADMIN' ? [] : form.projectIds };
    const request = form.id
      ? this.api.updateUser(form.id, { ...common, active: form.active, ...(form.password ? { password: form.password } : {}) })
      : this.api.createUser({ ...common, username: form.username.trim(), password: form.password });
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.saving.set(false); this.draft.set(null); this.load(); },
      error: (error: unknown) => { this.saving.set(false); this.formError.set(validationMessage(error)); },
    });
  }
}

/** Shows field errors from a 400 (for example "password: size must be between 8 and 128"). */
function validationMessage(error: unknown): string {
  const fields = (error as { error?: { errors?: Record<string, string> } }).error?.errors;
  return fields ? Object.entries(fields).map(([field, message]) => `${field}: ${message}`).join('; ') : errorMessage(error);
}
