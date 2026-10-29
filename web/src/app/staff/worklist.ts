import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClaimsApi } from '../api/claims.service';
import { ClaimStatus, ClaimSummary } from '../api/models';
import { AuthService } from '../core/auth';
import { age, errorMessage, money, poll, STAFF_STATUS } from '../core/format';
import { StatusChip } from '../shared/status-chip';

/**
 * Handler: the claims I own that are still moving, claimant replies first.
 * Manager: every claim, filterable by status.
 */
@Component({
  selector: 'app-worklist',
  imports: [RouterLink, FormsModule, StatusChip],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>{{ auth.isManager() ? 'All claims' : 'My claims' }}</h1>
          <p>{{ auth.isManager() ? 'Every claim in every team.' : 'Claims you own that are still open. Replies from claimants are listed first.' }}</p>
        </div>
        @if (auth.isManager()) {
          <div class="field filter">
            <label for="status">Status</label>
            <select id="status" [ngModel]="status()" (ngModelChange)="status.set($event); load()">
              <option [ngValue]="null">All</option>
              @for (s of statuses; track s) { <option [ngValue]="s">{{ label(s) }}</option> }
            </select>
          </div>
        }
      </div>

      @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }

      <div class="panel flush">
        @if (loaded() && !sorted().length) {
          <div class="empty">
            @if (auth.isManager()) { <p>No claims match this filter.</p> }
            @else { <p>You have no open claims.</p><p><a routerLink="/staff/queue">Pick one up from the queue.</a></p> }
          </div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Claim</th>
                  <th>Claimant</th>
                  <th>Status</th>
                  @if (auth.isManager()) { <th>Officer</th> }
                  <th class="right">Reserve</th>
                  <th>Last change</th>
                </tr>
              </thead>
              <tbody>
                @for (c of sorted(); track c.id) {
                  <tr class="clickable" (click)="open(c)">
                    <td><a [routerLink]="['/staff/claims', c.id]" (click)="$event.stopPropagation()" class="ref num">{{ c.reference }}</a></td>
                    <td>{{ c.claimantName }}</td>
                    <td><app-status-chip [status]="c.status" /></td>
                    @if (auth.isManager()) { <td class="small">{{ c.assignedHandlerName ?? '—' }}</td> }
                    <td class="right num">{{ money(c.reserveAmount, c.currency) }}</td>
                    <td class="num small muted">{{ ago(c.updatedAt) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `,
  styles: `
    .notice { margin-bottom: var(--space-4); }
    .flush { padding: var(--space-2) var(--space-4); }
    .ref { font-weight: 600; text-decoration: none; }
    .filter { min-width: 200px; }
  `,
})
export class Worklist {
  private api = inject(ClaimsApi);
  private router = inject(Router);
  protected auth = inject(AuthService);

  readonly statuses = Object.keys(STAFF_STATUS) as ClaimStatus[];
  readonly status = signal<ClaimStatus | null>(null);
  readonly claims = signal<ClaimSummary[]>([]);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  /** Claimant has replied (back to UNDER_ASSESSMENT) or waiting on them: surface what needs the officer first. */
  readonly sorted = computed(() => {
    const rank: Partial<Record<ClaimStatus, number>> = { UNDER_ASSESSMENT: 0, ASSIGNED: 1, APPROVED: 2, AWAITING_INFO: 3 };
    if (this.auth.isManager()) return this.claims();
    return [...this.claims()].sort((a, b) => (rank[a.status!] ?? 9) - (rank[b.status!] ?? 9));
  });

  constructor() {
    this.load();
    poll(() => this.load(), 15000);
  }

  load(): void {
    const req = this.auth.isManager() ? this.api.search(this.status() ?? undefined) : this.api.assigned();
    req.subscribe({
      next: c => { this.claims.set(c); this.loaded.set(true); this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  open(c: ClaimSummary): void {
    this.router.navigate(['/staff/claims', c.id]);
  }

  label = (s: ClaimStatus) => STAFF_STATUS[s];
  ago = (iso?: string) => { const a = age(iso); return a === 'just now' || a === '—' ? a : a + ' ago'; };
  money = money;
}
