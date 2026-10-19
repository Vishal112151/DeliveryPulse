import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideClock as Clock, LucidePlay as Play } from '@lucide/angular';
import { JobStatus } from '../core/api.models';
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
      </section>
    }
  `,
  styles: [`
    .automation { margin-top:15px; padding:18px 20px; border:1px solid #e5e9e3; border-radius:7px; background:#fff; }
    .heading { display:flex; align-items:center; justify-content:space-between; color:#7f8b83; }.heading h2 { margin:5px 0 0; color:#293a32; font-size:15px; }
    .eyebrow { color:#829087; font-size:9px; font-weight:800; letter-spacing:1.35px; }
    .job-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin-top:14px; }
    .job { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; padding:12px; border:1px solid #edf0ec; border-radius:6px; background:#fafbf9; }
    .job-main { display:grid; gap:4px; min-width:0; }.job-main strong { color:#33443a; font-size:11px; }.job-main span { color:#6f7b73; font-size:10px; }.job-main small { color:#8a948c; font-size:9px; line-height:1.4; }.job-main small.failed { color:#ad5142; }
    .run-button { display:inline-flex; flex:none; align-items:center; gap:6px; min-height:30px; padding:0 10px; border:1px solid #dfe5de; border-radius:5px; color:#3c5146; background:#fff; font:inherit; font-size:10px; font-weight:700; cursor:pointer; }.run-button:disabled { opacity:.55; cursor:wait; }
    .job-error { margin-top:10px; padding:9px 10px; border-radius:5px; color:#ad5142; background:#fff4f0; font-size:10px; }
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

  constructor() { this.load(); }

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
