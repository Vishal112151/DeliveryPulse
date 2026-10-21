import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideRefreshCw as RefreshCw } from '@lucide/angular';
import { AuthService } from '../core/auth.service';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { LucideIconCompatComponent } from './lucide-icon-compat.component';
import { errorMessage } from './view-helpers';

/** Runs risk analysis for every given project in turn, showing progress, then emits `finished`. */
@Component({
  selector: 'app-analyze-all-button',
  imports: [LucideIconCompatComponent],
  template: `
    @if (auth.canEdit()) {
    <button class="analyze-all" type="button" (click)="run()" [disabled]="running() || !projectIds().length">
      @if (running()) {
        <span class="spinner" aria-hidden="true"></span> Analyzing {{ done() }} of {{ projectIds().length }}…
      } @else {
        <lucide-icon [img]="RefreshIcon" [size]="15" /> Analyze all projects
      }
    </button>
    @if (error()) { <span class="analyze-error" role="alert">{{ error() }}</span> }
    }
  `,
  styles: [`
    :host { display: inline-flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .analyze-all { display: inline-flex; min-height: 36px; align-items: center; gap: 8px; padding: 0 14px; border: 1px solid #b6dc72;
      border-radius: 6px; color: #1e3a25; background: #c1ef74; font: inherit; font-size: 14.5px; font-weight: 700; cursor: pointer; }
    .analyze-all:hover:not(:disabled) { background: #b2e45f; }
    .analyze-all:disabled { cursor: default; opacity: .7; }
    .spinner { width: 14px; height: 14px; border: 2px solid #9fc65f; border-top-color: #1e3a25; border-radius: 50%; animation: spin .7s linear infinite; }
    .analyze-error { color: #b2402c; font-size: 13px; }
    @keyframes spin { to { transform: rotate(360deg); } }
  `],
})
export class AnalyzeAllButtonComponent {
  readonly projectIds = input.required<string[]>();
  readonly finished = output<void>();
  readonly running = signal(false);
  readonly done = signal(0);
  readonly error = signal('');
  protected readonly RefreshIcon = RefreshCw;
  protected readonly auth = inject(AuthService);
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);

  run(): void {
    const total = this.projectIds().length;
    this.running.set(true); this.done.set(0); this.error.set('');
    this.api.analyzeAll(this.projectIds()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.done.update((count) => count + 1),
      error: (error: unknown) => {
        this.running.set(false);
        this.error.set(`Stopped after ${this.done()} of ${total} projects: ${errorMessage(error)}`);
        this.finished.emit();
      },
      complete: () => { this.running.set(false); this.finished.emit(); },
    });
  }
}
