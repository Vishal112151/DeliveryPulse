import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideActivity as Activity } from '@lucide/angular';
import { AuthService } from '../core/auth.service';
import { LucideIconCompatComponent } from '../shared/lucide-icon-compat.component';
import { errorMessage } from '../shared/view-helpers';

@Component({
  standalone: true,
  imports: [FormsModule, LucideIconCompatComponent],
  template: `
    <main class="login-screen">
      <form class="login-card" (submit)="$event.preventDefault(); submit()">
        <div class="brand"><span class="brand-mark"><lucide-icon [img]="ActivityIcon" [size]="19" /></span><span class="brand-name">delivery<span>pulse</span></span></div>
        <h1>Sign in</h1>
        <p>Project delivery risk radar</p>
        <label><span>Username</span><input name="username" autocomplete="username" [(ngModel)]="username" required autofocus /></label>
        <label><span>Password</span><input name="password" type="password" autocomplete="current-password" [(ngModel)]="password" required /></label>
        @if (error()) { <div class="login-error" role="alert">{{ error() }}</div> }
        <button class="button-primary" type="submit" [disabled]="busy() || !username.trim() || !password">
          @if (busy()) { <span class="spinner"></span> Signing in… } @else { Sign in }
        </button>
        <small>Ask your DeliveryPulse admin for an account.</small>
      </form>
    </main>
  `,
  styles: [`
    :host { display:block; }
    .login-screen { display:grid; min-height:100vh; place-items:center; padding:16px; background:#182b27; }
    .login-card { display:grid; width:min(360px,100%); gap:12px; padding:28px; border-radius:10px; background:#fff; box-shadow:0 20px 50px rgb(0 0 0 / 25%); }
    .brand { display:flex; align-items:center; gap:10px; margin-bottom:8px; }.brand-mark { display:grid; width:32px; height:32px; place-items:center; border-radius:9px; color:#19332d; background:#c1ef74; }
    .brand-name { color:#182b27; font-size:17px; font-weight:750; }.brand-name span { color:#6d8a58; }
    h1 { margin:0; color:#23332d; font-size:22px; }p { margin:-6px 0 6px; color:#77827a; font-size:14.5px; }
    label { display:grid; gap:5px; }label span { color:#66746a; font-size:12px; font-weight:750; text-transform:uppercase; letter-spacing:.5px; }
    input { height:38px; padding:0 10px; border:1px solid #dfe5de; border-radius:6px; outline-color:#a7c28c; font:inherit; font-size:15.5px; }
    .button-primary { display:inline-flex; min-height:40px; align-items:center; justify-content:center; gap:8px; margin-top:4px; border:0; border-radius:6px; color:#1d3329; background:#c1ef74; font:inherit; font-size:15.5px; font-weight:750; cursor:pointer; }
    .button-primary:disabled { opacity:.55; cursor:not-allowed; }
    .login-error { padding:9px 10px; border-radius:5px; color:#ad5142; background:#fff4f0; font-size:14.5px; }
    small { color:#8a948c; font-size:13px; text-align:center; }
    .spinner { width:13px; height:13px; border:2px solid #dce5d8; border-top-color:#6d8a58; border-radius:50%; animation:spin .7s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
  `],
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  username = '';
  password = '';
  readonly busy = signal(false);
  readonly error = signal('');
  protected readonly ActivityIcon = Activity;

  submit(): void {
    if (this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.auth.login(this.username.trim(), this.password).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.busy.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        // Only follow in-app paths, never an absolute URL from the query string.
        void this.router.navigateByUrl(returnUrl?.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/dashboard');
      },
      error: (error: unknown) => { this.busy.set(false); this.password = ''; this.error.set(errorMessage(error)); },
    });
  }
}
