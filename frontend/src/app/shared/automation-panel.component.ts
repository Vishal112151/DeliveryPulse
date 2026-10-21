import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideClock as Clock, LucideMail as Mail, LucidePlay as Play } from '@lucide/angular';
import { AlertStatus, JobStatus } from '../core/api.models';
import { AuthService } from '../core/auth.service';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { LucideIconCompatComponent } from './lucide-icon-compat.component';
import { errorMessage, formatDateTime } from './view-helpers';

/** Shows the scheduled jobs and lets the user run one now. Hidden if the jobs endpoint is unavailable. */
@Component({
  selector: 'app-automation-panel',
  standalone: true,
  imports: [LucideIconCompatComponent],
  template: `
    @if (jobs().length) {
      <section class="automation" aria-label="Automation">
        <div class="heading"><div><div class="eyebrow">AUTOMATION</div><h2>Scheduled jobs</h2></div><lucide-icon [img]="ClockIcon" [size]="16" /></div>
        @if (error()) { <div class="job-error" role="alert">{{ error() }}</div> }
        <div class="job-list">
          @for (job of jobs(); track job.name) {
            <article class="job">
              <div class="job-main">
                <strong>{{ job.name === 'refresh' ? 'Daily refresh' : 'Weekly report' }}</strong>
                <span>{{ job.description }}</span>
                <small>{{ job.scheduled ? 'Next run ' + formatDateTime(job.nextRunAt) + ' · cron ' + job.cron : 'Schedule off (SCHEDULE_ENABLED=false)' }}</small>
                @if (job.lastRun; as run) {
                  <small [class.failed]="!run.succeeded">Last run {{ formatDateTime(run.finishedAt) }}{{ run.manual ? ' (manual)' : '' }}: {{ run.succeeded ? '' : 'failed: ' }}{{ run.message }}</small>
                } @else { <small>Not run since the backend started</small> }
              </div>
              @if (auth.isAdmin()) {
                <button class="run-button" type="button" (click)="run(job.name)" [disabled]="busy() !== null || job.running">
                  @if (busy() === job.name || job.running) { <span class="spinner"></span> Running… } @else { <lucide-icon [img]="PlayIcon" [size]="13" /> Run now }
                </button>
              }
            </article>
          }
        </div>
        @if (alerts(); as status) {
          <article class="job alerts">
            <div class="job-main">
              <strong>Email alerts</strong>
              <span>New Critical/High risks after each refresh, and the weekly report summary</span>
              <small [class.failed]="!status.configured">{{ status.configured ? 'On · ' + status.recipientCount + ' recipient' + (status.recipientCount === 1 ? '' : 's') : 'Off: set MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD and ALERT_EMAIL_TO in .env' }}</small>
              @if (alertMessage()) { <small [class.failed]="alertFailed()">{{ alertMessage() }}</small> }
            </div>
            <button class="run-button" type="button" (click)="sendTestAlert()" [disabled]="!status.configured || sendingAlert()">
              @if (sendingAlert()) { <span class="spinner"></span> Sending… } @else { <lucide-icon [img]="MailIcon" [size]="13" /> Send test email }
            </button>
          </article>
        }
      </section>
    }
  `,
  styles: [`
    .automation { margin-top:15px; padding:18px 20px; border:1px solid #e5e9e3; border-radius:7px; background:#fff; }
    .heading { display:flex; align-items:center; justify-content:space-between; color:#7f8b83; }.heading h2 { margin:5px 0 0; color:#293a32; font-size:18px; }
    .eyebrow { color:#829087; font-size:11px; font-weight:800; letter-spacing:1.35px; }
    .job-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin-top:14px; }
    .job { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:12px; border:1px solid #edf0ec; border-radius:6px; background:#fafbf9; }
    .job-main { display:grid; gap:4px; min-width:0; }.job-main strong { color:#33443a; font-size:13px; }.job-main span { color:#6f7b73; font-size:12px; }.job-main small { color:#8a948c; font-size:11px; line-height:1.4; }.job-main small.failed { color:#ad5142; }
    .run-button { display:inline-flex; flex:none; align-items:center; gap:6px; min-height:30px; padding:0 10px; border:1px solid #dfe5de; border-radius:5px; color:#3c5146; background:#fff; font:inherit; font-size:12px; font-weight:700; cursor:pointer; }.run-button:disabled { opacity:.55; cursor:wait; }
    .job.alerts { margin-top:10px; }
    .job-error { margin-top:10px; padding:9px 10px; border-radius:5px; color:#ad5142; background:#fff4f0; font-size:12px; }
    .spinner { width:11px; height:11px; border:2px solid #dce5d8; border-top-color:#6d8a58; border-radius:50%; animation:spin .7s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:700px){ .job-list { grid-template-columns:1fr; } }
  `],
})
export class AutomationPanelComponent {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly jobs = signal<JobStatus[]>([]);
  readonly busy = signal<JobStatus['name'] | null>(null);
  readonly error = signal('');
  protected readonly ClockIcon = Clock;
  protected readonly PlayIcon = Play;
  protected readonly formatDateTime = formatDateTime;

  readonly alerts = signal<AlertStatus | null>(null);
  readonly sendingAlert = signal(false);
  readonly alertMessage = signal('');
  readonly alertFailed = signal(false);
  protected readonly MailIcon = Mail;

  constructor() {
    this.load();
    if (this.auth.isAdmin()) {
      this.api.getAlertStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (status) => this.alerts.set(status),
        error: () => this.alerts.set(null),
      });
    }
  }

  sendTestAlert(): void {
    this.sendingAlert.set(true); this.alertMessage.set('');
    this.api.sendTestAlert().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => { this.sendingAlert.set(false); this.alertFailed.set(false); this.alertMessage.set(result.message); },
      error: (error: unknown) => { this.sendingAlert.set(false); this.alertFailed.set(true); this.alertMessage.set(errorMessage(error)); },
    });
  }

  load(): void {
    this.api.getJobs().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (jobs) => this.jobs.set(jobs),
      error: () => this.jobs.set([]),
    });
  }

  run(name: JobStatus['name']): void {
    this.busy.set(name); this.error.set('');
    this.api.runJob(name).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.busy.set(null); this.load(); },
      error: (error: unknown) => { this.busy.set(null); this.error.set(errorMessage(error)); this.load(); },
    });
  }
}
