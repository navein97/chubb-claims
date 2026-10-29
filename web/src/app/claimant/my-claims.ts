import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClaimsApi } from '../api/claims.service';
import { ClaimSummary } from '../api/models';
import { errorMessage, poll, shortDate } from '../core/format';
import { StatusChip } from '../shared/status-chip';

@Component({
  selector: 'app-my-claims',
  imports: [RouterLink, StatusChip],
  template: `
    <div class="page page--narrow">
      <div class="page-head">
        <div>
          <h1>Your claims</h1>
          <p>Track progress and reply to your claims officer.</p>
        </div>
        <a class="btn btn--primary" routerLink="/claimant/new">Report an incident</a>
      </div>

      @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }

      @if (waiting().length) {
        <p class="notice notice--attention waiting">
          {{ waiting().length === 1 ? 'One claim needs' : waiting().length + ' claims need' }} more information from you.
        </p>
      }

      @if (loaded() && !claims().length) {
        <div class="panel empty">
          <p>You haven't reported any incidents yet.</p>
          <p><a routerLink="/claimant/new">Report an incident</a> to start a claim.</p>
        </div>
      }

      <ul class="list">
        @for (c of claims(); track c.id) {
          <li>
            <a class="row" [class.row--waiting]="c.status === 'AWAITING_INFO'" [routerLink]="['/claimant', c.id]">
              <span class="what">
                <span class="title">{{ c.type === 'MOTOR' ? 'Motor' : 'Property' }} claim</span>
                <span class="ref num">{{ c.reference }}</span>
              </span>
              <span class="when small muted">Reported {{ date(c.submittedAt) }}</span>
              <app-status-chip [status]="c.status" audience="claimant" />
            </a>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .waiting { margin-bottom: var(--space-4); }
    .list { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
    .row {
      display: grid;
      grid-template-columns: 1fr auto auto;
      align-items: center;
      gap: var(--space-4);
      padding: var(--space-4) var(--space-5);
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius-lg);
      color: var(--ink);
      text-decoration: none;
    }
    .row:hover { border-color: var(--line-strong); color: var(--ink); }
    .row--waiting { border-color: var(--attention-line); border-left: 4px solid var(--attention); }
    .what { display: flex; flex-direction: column; }
    .title { font-weight: 600; }
    .ref { font-size: var(--text-sm); color: var(--muted); }
    @media (max-width: 560px) {
      .row { grid-template-columns: 1fr auto; }
      .when { display: none; }
    }
  `,
})
export class MyClaims {
  private api = inject(ClaimsApi);

  readonly claims = signal<ClaimSummary[]>([]);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  readonly waiting = computed(() => this.claims().filter(c => c.status === 'AWAITING_INFO'));

  constructor() {
    this.load();
    poll(() => this.load(), 15000);
  }

  load(): void {
    this.api.mine().subscribe({
      next: c => { this.claims.set(c); this.loaded.set(true); this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  date = shortDate;
}
