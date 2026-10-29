import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ClaimsApi } from '../api/claims.service';
import { ClaimAction, ClaimDetail } from '../api/models';
import { errorMessage, money, poll, shortDate } from '../core/format';
import { StatusChip } from '../shared/status-chip';
import { StatusTrack } from '../shared/status-track';
import { Timeline } from '../shared/timeline';

/**
 * Staff view of one claim. Every button here is rendered from `allowedActions`, which the backend
 * computes for the signed-in user, so the UI never offers something the server would refuse.
 */
@Component({
  selector: 'app-staff-claim',
  imports: [RouterLink, FormsModule, StatusChip, StatusTrack, Timeline],
  template: `
    <div class="page">
      <a class="back small" routerLink="/staff/mine">Back to claims</a>

      @if (claim(); as c) {
        <header class="head">
          <div>
            <h1 class="num">{{ c.reference }}</h1>
            <p class="muted">
              {{ c.type === 'MOTOR' ? 'Motor' : 'Property' }}, {{ c.team }}.
              {{ c.assignedHandlerName ? 'Owned by ' + c.assignedHandlerName + '.' : 'Not picked up yet.' }}
            </p>
          </div>
          <app-status-chip [status]="c.status" />
        </header>

        <div class="track-wrap panel"><app-status-track [status]="c.status!" [compact]="true" /></div>

        @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }
        @if (done()) { <p class="notice notice--ok" role="status">{{ done() }}</p> }

        <div class="grid">
          <div class="main">
            <section class="panel" aria-labelledby="incident">
              <h2 id="incident">Incident</h2>
              <dl class="facts">
                <div><dt>Claimant</dt><dd>{{ c.claimantName }}</dd></div>
                <div><dt>Policy</dt><dd class="num">{{ c.policyNumber }}</dd></div>
                <div><dt>Incident date</dt><dd>{{ date(c.incidentDate) }}</dd></div>
                <div><dt>Reported</dt><dd>{{ date(c.submittedAt) }}</dd></div>
              </dl>
              <p class="desc">{{ c.description }}</p>
            </section>

            @if (c.infoRequests?.length) {
              <section class="panel" aria-labelledby="info">
                <h2 id="info">Information requests</h2>
                @for (r of c.infoRequests; track r.id) {
                  <div class="qa">
                    <p class="q">{{ r.question }}</p>
                    @if (r.open) { <p class="pending small">Waiting for the claimant to reply</p> }
                    @else { <p class="a">{{ r.response }}</p> }
                  </div>
                }
              </section>
            }

            <section class="panel" aria-labelledby="history">
              <h2 id="history">History</h2>
              <app-timeline [entries]="c.timeline" />
            </section>
          </div>

          <aside class="side">
            <section class="panel money" aria-labelledby="money">
              <h2 id="money">Money</h2>
              <dl>
                <div><dt>Claimant's estimate</dt><dd class="num">{{ money(c.estimatedLoss, c.currency) }}</dd></div>
                <div class="reserve"><dt>Reserve (outstanding)</dt><dd class="num">{{ money(c.reserveAmount, c.currency) }}</dd></div>
                @if (c.settledAmount != null) {
                  <div><dt>{{ c.status === 'SETTLED' ? 'Paid' : 'Approved' }}</dt><dd class="num">{{ money(c.settledAmount, c.currency) }}</dd></div>
                }
              </dl>
              @if (c.decisionReason) { <p class="small muted reason">{{ c.decisionReason }}</p> }
            </section>

            <section class="panel" aria-labelledby="actions">
              <h2 id="actions">Next step</h2>

              @if (!actions().size) {
                <p class="muted small">{{ noActionText() }}</p>
              }

              @if (can('PICK_UP')) {
                <button class="btn btn--primary wide" [disabled]="busy()" (click)="run(api.pickUp(c.id!), 'Claim picked up.')">Pick up claim</button>
              }
              @if (can('START_ASSESSMENT')) {
                <button class="btn btn--primary wide" [disabled]="busy()" (click)="run(api.startAssessment(c.id!), 'Assessment started.')">Start assessment</button>
              }
              @if (can('SETTLE')) {
                <p class="small muted">Confirm once payment of {{ money(c.settledAmount, c.currency) }} has been made.</p>
                <button class="btn btn--primary wide" [disabled]="busy()" (click)="run(api.settle(c.id!), 'Marked as paid. Claim closed.')">Mark as paid</button>
              }

              @if (can('APPROVE') || can('REJECT') || can('REQUEST_INFO')) {
                <div class="tabs" role="tablist" aria-label="Decision">
                  @if (can('APPROVE')) { <button role="tab" type="button" [attr.aria-selected]="tab() === 'approve'" (click)="tab.set('approve')">Approve</button> }
                  @if (can('REQUEST_INFO')) { <button role="tab" type="button" [attr.aria-selected]="tab() === 'info'" (click)="tab.set('info')">Ask claimant</button> }
                  @if (can('REJECT')) { <button role="tab" type="button" [attr.aria-selected]="tab() === 'reject'" (click)="tab.set('reject')">Reject</button> }
                </div>

                @if (tab() === 'approve' && can('APPROVE')) {
                  <form (ngSubmit)="approve(c)" class="stack">
                    <div class="field">
                      <label for="amount">Amount to pay ({{ c.currency }})</label>
                      <input id="amount" name="amount" type="number" min="0.01" step="0.01" [(ngModel)]="approveAmount" required />
                    </div>
                    <div class="field">
                      <label for="approve-reason">Note for the claimant (optional)</label>
                      <input id="approve-reason" name="approveReason" [(ngModel)]="approveReason" />
                    </div>
                    @if (overLimit(c)) {
                      <p class="notice notice--attention small" role="status">
                        Above your approval limit of {{ money(c.approvalLimit, c.currency) }}. Ask a claims manager to approve this claim.
                      </p>
                    } @else if (c.approvalLimit != null) {
                      <p class="small muted">You can approve up to {{ money(c.approvalLimit, c.currency) }}.</p>
                    }
                    <button class="btn btn--primary" type="submit" [disabled]="busy() || !(approveAmount > 0) || overLimit(c)">Approve claim</button>
                  </form>
                }
                @if (tab() === 'info' && can('REQUEST_INFO')) {
                  <form (ngSubmit)="requestInfo(c)" class="stack">
                    <div class="field">
                      <label for="question">What do you need from the claimant?</label>
                      <textarea id="question" name="question" rows="3" [(ngModel)]="question" required></textarea>
                    </div>
                    <button class="btn btn--primary" type="submit" [disabled]="busy() || !question.trim()">Send request</button>
                  </form>
                }
                @if (tab() === 'reject' && can('REJECT')) {
                  <form (ngSubmit)="reject(c)" class="stack">
                    <div class="field">
                      <label for="reject-reason">Reason (the claimant will see this)</label>
                      <textarea id="reject-reason" name="rejectReason" rows="3" [(ngModel)]="rejectReason" required></textarea>
                    </div>
                    <button class="btn btn--danger" type="submit" [disabled]="busy() || !rejectReason.trim()">Reject claim</button>
                  </form>
                }
              }

              @if (can('UPDATE_RESERVE')) {
                <details class="reserve-form" [open]="reserveOpen()" (toggle)="reserveOpen.set($any($event.target).open)">
                  <summary>Adjust reserve</summary>
                  <form (ngSubmit)="updateReserve(c)" class="stack">
                    <div class="field">
                      <label for="reserve">New reserve ({{ c.currency }})</label>
                      <input id="reserve" name="reserve" type="number" min="0" step="0.01" [(ngModel)]="reserveAmount" required />
                    </div>
                    <div class="field">
                      <label for="reserve-reason">Why</label>
                      <input id="reserve-reason" name="reserveReason" [(ngModel)]="reserveReason" placeholder="e.g. Workshop quote received" />
                    </div>
                    <button class="btn" type="submit" [disabled]="busy() || reserveAmount < 0">Update reserve</button>
                  </form>
                </details>
              }
            </section>
          </aside>
        </div>
      } @else if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }
    </div>
  `,
  styles: `
    .back { display: inline-block; margin-bottom: var(--space-4); }
    .back::before { content: '‹ '; }
    .head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-4); margin-bottom: var(--space-4); }
    .head p { margin-top: var(--space-1); }
    .track-wrap { padding: var(--space-4) var(--space-5); margin-bottom: var(--space-4); }
    .notice { margin-bottom: var(--space-4); }
    .grid { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: var(--space-4); align-items: start; }
    .main .panel + .panel, .side .panel + .panel { margin-top: var(--space-4); }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } .side { order: -1; } }

    .facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: var(--space-4); margin: 0 0 var(--space-4); }
    dt { font-size: var(--text-sm); color: var(--muted); }
    dd { margin: 0; font-weight: 600; }
    .desc { white-space: pre-line; max-width: 70ch; }
    .qa + .qa { margin-top: var(--space-3); padding-top: var(--space-3); border-top: 1px solid var(--line); }
    .q { font-weight: 600; }
    .a { white-space: pre-line; }
    .pending { color: var(--attention); font-weight: 600; }

    .money dl { margin: 0; display: grid; gap: var(--space-3); }
    .money dl > div { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-3); }
    .money .reserve { padding: var(--space-3) 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
    .money .reserve dd { font-size: var(--text-xl); color: var(--primary); }
    .reason { margin-top: var(--space-3); }

    .wide { width: 100%; }
    .stack { display: grid; gap: var(--space-3); margin-top: var(--space-3); }
    .tabs { display: flex; border-bottom: 1px solid var(--line); gap: var(--space-1); }
    .tabs button {
      font: inherit; font-weight: 600; font-size: var(--text-sm);
      background: none; border: 0; padding: var(--space-2) var(--space-3);
      color: var(--muted); cursor: pointer; border-bottom: 3px solid transparent; margin-bottom: -1px;
    }
    .tabs button[aria-selected='true'] { color: var(--ink); border-bottom-color: var(--primary); }
    .reserve-form { margin-top: var(--space-4); padding-top: var(--space-3); border-top: 1px solid var(--line); }
    .reserve-form summary { cursor: pointer; font-weight: 600; font-size: var(--text-sm); color: var(--primary); }
  `,
})
export class StaffClaim {
  protected api = inject(ClaimsApi);

  readonly id = input.required<string>();
  readonly claim = signal<ClaimDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly done = signal<string | null>(null);
  readonly busy = signal(false);
  readonly tab = signal<'approve' | 'info' | 'reject'>('approve');
  readonly reserveOpen = signal(false);

  approveAmount = 0;
  approveReason = '';
  question = '';
  rejectReason = '';
  reserveAmount = 0;
  reserveReason = '';

  readonly actions = computed(() => new Set<ClaimAction>(this.claim()?.allowedActions ?? []));
  readonly noActionText = computed(() => {
    const c = this.claim();
    switch (c?.status) {
      case 'AWAITING_INFO': return 'Waiting for the claimant to reply. This page updates when they do.';
      case 'SETTLED': return 'Paid and closed. No further action.';
      case 'REJECTED': return 'Rejected and closed. No further action.';
      case 'SUBMITTED': return 'Waiting for a claims officer in this team to pick it up.';
      default: return 'Only the assigned claims officer or a manager can act on this claim.';
    }
  });

  constructor() {
    effect(() => this.load(this.id()));
    poll(() => this.load(this.id()), 15000);
  }

  /** UI hint only: the backend rejects over-limit approvals with 403 regardless. */
  overLimit(c: ClaimDetail): boolean {
    return c.approvalLimit != null && Number(this.approveAmount) > c.approvalLimit;
  }

  can(action: ClaimAction): boolean {
    return this.actions().has(action);
  }

  /** keepError: after a failed action, refresh the claim but leave the error visible. */
  load(id: string, keepError = false): void {
    this.api.get(id).subscribe({
      next: c => { this.apply(c); if (!keepError) this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  run(request: Observable<ClaimDetail>, message: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.done.set(null);
    request.subscribe({
      next: c => { this.apply(c, true); this.done.set(message); this.busy.set(false); },
      error: e => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
        this.load(this.id(), true); // state may have moved under us (409): show the latest
      },
    });
  }

  approve(c: ClaimDetail): void {
    this.run(this.api.approve(c.id!, Number(this.approveAmount), this.approveReason.trim()), 'Claim approved. The claimant can see the decision.');
  }

  requestInfo(c: ClaimDetail): void {
    this.run(this.api.requestInfo(c.id!, this.question.trim()), 'Request sent. The claim is waiting on the claimant.');
  }

  reject(c: ClaimDetail): void {
    this.run(this.api.reject(c.id!, this.rejectReason.trim()), 'Claim rejected. The claimant can see the reason.');
  }

  updateReserve(c: ClaimDetail): void {
    this.run(this.api.updateReserve(c.id!, Number(this.reserveAmount), this.reserveReason.trim()), 'Reserve updated.');
  }

  /** Keep form inputs while polling; reset them after this user's own action succeeds. */
  private apply(c: ClaimDetail, afterAction = false): void {
    const first = this.claim() === null;
    this.claim.set(c);
    if (first || afterAction) {
      this.approveAmount = c.reserveAmount ?? 0;
      this.reserveAmount = c.reserveAmount ?? 0;
      this.approveReason = '';
      this.question = '';
      this.rejectReason = '';
      this.reserveReason = '';
      this.reserveOpen.set(false);
      this.tab.set(c.allowedActions?.includes('APPROVE') ? 'approve' : 'info');
    }
  }

  money = money;
  date = shortDate;
}
