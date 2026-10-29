import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ClaimsApi } from '../api/claims.service';
import { Exposure, Workload } from '../api/models';
import { AuthService } from '../core/auth';
import { age, errorMessage, money, poll } from '../core/format';

/**
 * Read-side view fed by the insights service (Kafka projection). Numbers are a few hundred
 * milliseconds behind the claims service, so the page always says how fresh they are.
 */
@Component({
  selector: 'app-dashboard',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>{{ auth.isManager() ? 'Exposure and workload' : 'Team ' + auth.user()?.team }}</h1>
          <p>{{ freshness() }}</p>
        </div>
        @if (auth.isManager()) {
          <div class="field filter">
            <label for="team">Team</label>
            <select id="team" [ngModel]="team()" (ngModelChange)="team.set($event); load()">
              <option [ngValue]="null">All teams</option>
              @for (t of knownTeams(); track t) { <option [ngValue]="t">{{ t }}</option> }
            </select>
          </div>
        }
      </div>

      @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }

      @if (exposure(); as ex) {
        <section aria-labelledby="exposure-title">
          <h2 id="exposure-title" class="section-title">Outstanding liability</h2>
          <p class="muted small explain">Sum of reserves on every open claim. Shown per currency; no FX conversion.</p>
          @if (!ex.totals?.length) {
            <div class="panel empty"><p>No open claims, so nothing is outstanding.</p></div>
          } @else {
            <div class="totals">
              @for (t of ex.totals; track t.currency) {
                <div class="total panel">
                  <span class="amount num">{{ money(t.outstandingReserve, t.currency) }}</span>
                  <span class="small muted">{{ t.openClaims }} open {{ (t.openClaims ?? 0) === 1 ? 'claim' : 'claims' }}</span>
                </div>
              }
            </div>
            <div class="panel flush">
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Market</th><th>Line</th><th class="right">Open claims</th><th class="right">Outstanding reserve</th></tr></thead>
                  <tbody>
                    @for (r of ex.rows; track $index) {
                      <tr>
                        <td>{{ r.market }}</td>
                        <td>{{ r.claimType === 'MOTOR' ? 'Motor' : 'Property' }}</td>
                        <td class="right num">{{ r.openClaims }}</td>
                        <td class="right num">{{ money(r.outstandingReserve, r.currency) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }
        </section>
      }

      @if (workload(); as wl) {
        <section aria-labelledby="teams-title">
          <h2 id="teams-title" class="section-title">Teams</h2>
          <div class="panel flush">
            @if (!wl.teams?.length) {
              <div class="empty"><p>No claims yet.</p></div>
            } @else {
              <div class="table-wrap">
                <table class="data">
                  <thead>
                    <tr>
                      <th>Team</th>
                      <th class="right">Unassigned</th>
                      <th>Oldest waiting</th>
                      <th class="right">In progress</th>
                      <th class="right">Awaiting claimant</th>
                      <th class="right">Closed, 30 days</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (t of wl.teams; track t.team) {
                      <tr>
                        <td><strong>{{ t.team }}</strong></td>
                        <td class="right num" [class.hot]="(t.unassigned ?? 0) > 0">{{ t.unassigned }}</td>
                        <td class="num">{{ t.oldestUnassignedAt ? since(t.oldestUnassignedAt) : '—' }}</td>
                        <td class="right num">{{ t.inProgress }}</td>
                        <td class="right num">{{ t.awaitingInfo }}</td>
                        <td class="right num">{{ t.closedLast30Days }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </section>

        <section aria-labelledby="officers-title">
          <h2 id="officers-title" class="section-title">Claims officers</h2>
          <div class="panel flush">
            @if (!wl.handlers?.length) {
              <div class="empty"><p>No claims have been picked up yet.</p></div>
            } @else {
              <div class="table-wrap">
                <table class="data">
                  <thead>
                    <tr>
                      <th>Officer</th>
                      <th>Team</th>
                      <th class="right">Open</th>
                      <th>Load</th>
                      <th class="right">Awaiting claimant</th>
                      <th class="right">Closed, 30 days</th>
                      <th class="right">Avg. days to close</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (h of wl.handlers; track h.handlerId) {
                      <tr>
                        <td><strong>{{ h.handlerName }}</strong></td>
                        <td class="small">{{ h.team }}</td>
                        <td class="right num">{{ h.open }}</td>
                        <td class="bar-cell" aria-hidden="true"><span class="bar" [style.width.%]="loadPct(h.open)"></span></td>
                        <td class="right num">{{ h.awaitingInfo }}</td>
                        <td class="right num">{{ h.closedLast30Days }}</td>
                        <td class="right num">{{ h.avgCycleDays ?? '—' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .filter { min-width: 200px; }
    .notice { margin-bottom: var(--space-4); }
    section + section { margin-top: var(--space-6); }
    .section-title { margin-bottom: var(--space-1); }
    .explain { margin-bottom: var(--space-3); }
    .section-title + .panel { margin-top: var(--space-3); }
    .totals { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--space-3); margin-bottom: var(--space-3); }
    .total { display: flex; flex-direction: column; gap: var(--space-1); padding: var(--space-4) var(--space-5); }
    .total + .total { margin-top: 0; }
    .amount { font-size: var(--text-xl); font-weight: 700; letter-spacing: -0.01em; }
    .flush { padding: var(--space-2) var(--space-4); }
    .hot { color: var(--attention); font-weight: 700; }
    .bar-cell { width: 22%; min-width: 90px; }
    .bar { display: block; height: 8px; border-radius: 4px; background: var(--primary); min-width: 2px; }
  `,
})
export class Dashboard {
  private api = inject(ClaimsApi);
  protected auth = inject(AuthService);

  readonly team = signal<string | null>(null);
  readonly exposure = signal<Exposure | null>(null);
  readonly workload = signal<Workload | null>(null);
  readonly error = signal<string | null>(null);
  readonly knownTeams = signal<string[]>([]);
  private now = signal(Date.now());

  readonly freshness = computed(() => {
    const asOf = this.exposure()?.asOf ?? this.workload()?.asOf;
    if (!asOf) return 'Waiting for the first claim event.';
    const a = age(asOf, this.now());
    return `Includes changes up to ${a === 'just now' ? 'just now' : a + ' ago'}. Refreshes every 10 seconds.`;
  });

  private maxOpen = computed(() => Math.max(1, ...(this.workload()?.handlers ?? []).map(h => h.open ?? 0)));

  constructor() {
    this.load();
    poll(() => this.load(), 10000);
  }

  load(): void {
    const team = this.team() ?? undefined;
    forkJoin({ ex: this.api.exposure(team), wl: this.api.workload(team) }).subscribe({
      next: ({ ex, wl }) => {
        this.exposure.set(ex);
        this.workload.set(wl);
        this.now.set(Date.now());
        this.error.set(null);
        if (!this.team()) this.knownTeams.set((wl.teams ?? []).map(t => t.team!));
      },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  loadPct = (open?: number) => Math.round(((open ?? 0) / this.maxOpen()) * 100);
  since = (iso: string) => age(iso, this.now());
  money = money;
}
