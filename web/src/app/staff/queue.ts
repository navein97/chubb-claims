import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ClaimsApi } from '../api/claims.service';
import { ClaimSummary } from '../api/models';
import { AuthService } from '../core/auth';
import { age, errorMessage, money, poll } from '../core/format';

/** Unassigned claims, oldest first. Handlers see their team; managers see every team. */
@Component({
  selector: 'app-queue',
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Queue</h1>
          <p>{{ auth.isManager() ? 'Unassigned claims across all teams, oldest first.' : 'Unassigned claims for ' + auth.user()?.team + ', oldest first.' }}</p>
        </div>
        <span class="count num">{{ claims().length }} waiting</span>
      </div>

      @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }
      @if (flash()) { <p class="notice notice--ok" role="status">{{ flash() }}</p> }

      <div class="panel flush">
        @if (loaded() && !claims().length) {
          <div class="empty"><p>No unassigned claims. New claims appear here as they come in.</p></div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Claim</th>
                  <th>Claimant</th>
                  @if (auth.isManager()) { <th>Team</th> }
                  <th>Waiting</th>
                  <th class="right">Estimate</th>
                  <th><span class="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                @for (c of claims(); track c.id) {
                  <tr class="clickable" (click)="open(c)">
                    <td>
                      <a [routerLink]="['/staff/claims', c.id]" (click)="$event.stopPropagation()" class="ref num">{{ c.reference }}</a>
                      <span class="small muted type">{{ c.type === 'MOTOR' ? 'Motor' : 'Property' }}</span>
                    </td>
                    <td>{{ c.claimantName }}</td>
                    @if (auth.isManager()) { <td class="small">{{ c.team }}</td> }
                    <td class="num" [class.old]="isOld(c)">{{ waited(c) }}</td>
                    <td class="right num">{{ money(c.reserveAmount, c.currency) }}</td>
                    <td class="right">
                      @if (!auth.isManager()) {
                        <button type="button" class="btn btn--primary pick" [disabled]="busyId() === c.id"
                                (click)="pickUp(c, $event)">
                          {{ busyId() === c.id ? 'Picking up…' : 'Pick up' }}
                        </button>
                      }
                    </td>
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
    .count { font-weight: 600; color: var(--muted); }
    .notice { margin-bottom: var(--space-4); }
    .flush { padding: var(--space-2) var(--space-4); }
    .ref { font-weight: 600; text-decoration: none; }
    .type { display: block; }
    .old { color: var(--attention); font-weight: 600; }
    .pick { min-height: 32px; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
})
export class Queue {
  private api = inject(ClaimsApi);
  private router = inject(Router);
  protected auth = inject(AuthService);

  readonly claims = signal<ClaimSummary[]>([]);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  readonly flash = signal<string | null>(null);
  readonly busyId = signal<string | null>(null);
  private now = signal(Date.now());

  constructor() {
    this.load();
    poll(() => this.load(), 15000);
  }

  load(): void {
    this.api.queue().subscribe({
      next: c => { this.claims.set(c); this.loaded.set(true); this.now.set(Date.now()); },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  pickUp(c: ClaimSummary, event: Event): void {
    event.stopPropagation();
    this.busyId.set(c.id!);
    this.error.set(null);
    this.api.pickUp(c.id!).subscribe({
      next: claim => this.router.navigate(['/staff/claims', claim.id]),
      error: e => {
        // Most likely another officer got there first (409): show why and refresh the list.
        this.busyId.set(null);
        this.error.set(errorMessage(e));
        this.load();
      },
    });
  }

  open(c: ClaimSummary): void {
    this.router.navigate(['/staff/claims', c.id]);
  }

  waited = (c: ClaimSummary) => age(c.submittedAt, this.now());
  isOld = (c: ClaimSummary) => !!c.submittedAt && this.now() - new Date(c.submittedAt).getTime() > 24 * 3600_000;
  money = money;
}
