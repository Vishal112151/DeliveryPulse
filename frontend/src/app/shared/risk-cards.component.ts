import { Component, input } from '@angular/core';
import { RiskResponse } from '../core/api.models';
import { titleCase } from './view-helpers';

@Component({
  selector: 'app-risk-cards',
  template: `
    @if (risks().length === 0) {
      <div class="empty-inline">No active findings for this selection.</div>
    } @else {
      <div class="risk-list">
        @for (risk of risks(); track risk.id) {
          <article class="risk-row">
            <span class="risk-marker" [class]="'risk-marker ' + risk.severity.toLowerCase()"></span>
            <div class="risk-main">
              <div class="risk-title-line">
                <h3>{{ risk.title }}</h3>
                <span class="certainty" [class.potential]="risk.certainty === 'POTENTIAL'" [class.quality]="risk.certainty === 'DATA_QUALITY'">{{ titleCase(risk.certainty) }}</span>
              </div>
              <p>{{ risk.explanation }}</p>
              @if (risk.supportingTaskIds.length) {
                <div class="evidence-line"><span>Evidence</span>{{ risk.supportingTaskIds.join(', ') }}<span class="evidence-detail">{{ risk.evidence }}</span></div>
              } @else if (risk.evidence) {
                <div class="evidence-line"><span>Evidence</span>{{ risk.evidence }}</div>
              }
              @if (risk.recommendedAction) {
                <div class="action-line"><span>Next action</span>{{ risk.recommendedAction }}</div>
              }
            </div>
            <span class="risk-category">{{ titleCase(risk.category) }}</span>
          </article>
        }
      </div>
    }
  `,
  styles: [`
    .risk-list { display: grid; }
    .risk-row { display: grid; grid-template-columns: 9px minmax(0, 1fr) auto; gap: 13px; align-items: start; padding: 17px 0; border-bottom: 1px solid #edf0ec; }
    .risk-marker { width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; background: #90a28f; }
    .risk-marker.critical { background: #d95a48; box-shadow: 0 0 0 4px #f9e7e3; }
    .risk-marker.high { background: #e99b45; box-shadow: 0 0 0 4px #fbf0df; }
    .risk-marker.medium { background: #d5b642; box-shadow: 0 0 0 4px #f9f4df; }
    .risk-marker.low { background: #80a976; box-shadow: 0 0 0 4px #edf4e9; }
    .risk-title-line { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; }
    h3 { margin: 0; color: #263630; font-size: 13px; font-weight: 700; }
    p { margin: 6px 0 0; color: #657169; font-size: 12px; line-height: 1.55; }
    .certainty { padding: 3px 6px; border-radius: 3px; color: #4f7049; background: #edf4e8; font-size: 9px; font-weight: 750; text-transform: uppercase; }
    .certainty.potential { color: #986b24; background: #fbf2df; }
    .certainty.quality { color: #68756c; background: #eef0ed; }
    .risk-category { color: #89938c; font-size: 10px; white-space: nowrap; }
    .evidence-line, .action-line { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 8px; color: #42564a; font-size: 11px; line-height: 1.45; }
    .evidence-line > span:first-child, .action-line > span:first-child { color: #89938c; font-size: 9px; font-weight: 750; letter-spacing: .5px; text-transform: uppercase; }
    .evidence-detail { color: #89938c; }
    .empty-inline { padding: 24px 0; color: #78837b; font-size: 12px; }
    @media (max-width: 650px) { .risk-row { grid-template-columns: 9px minmax(0, 1fr); } .risk-category { grid-column: 2; } }
  `],
})
export class RiskCardsComponent {
  readonly risks = input.required<RiskResponse[]>();
  protected readonly titleCase = titleCase;
}