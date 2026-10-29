import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClaimsApi } from '../api/claims.service';
import { ClaimDetail } from '../api/models';
import { errorMessage, money, poll, shortDate } from '../core/format';
import { StatusTrack } from '../shared/status-track';
import { Timeline } from '../shared/timeline';

@Component({
  selector: 'app-claimant-claim',
  imports: [RouterLink, FormsModule, StatusTrack, Timeline],
  template: `
    <div class="page page--narrow">
      <a class="back small" routerLink="/claimant">Your claims</a>

      @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }

      @if (claim(); as c) {
        @if (submitted()) {
          <p class="notice notice--ok" role="status">
            Claim {{ c.reference }} received. You can follow its progress on this page.
          </p>
        }

        <header class="head">
          <h1>{{ c.type === 'MOTOR' ? 'Motor' : 'Property' }} claim</h1>
          <p class="muted num">{{ c.reference }}</p>
        </header>

        <section class="panel progress" aria-labelledby="progress-title">
          <h2 id="progress-title" class="sr-only">Progress</h2>
          <app-status-track [status]="c.status!" />
          <p class="next">{{ nextStep() }}</p>
        </section>

        @for (r of openRequests(); track r.id) {
          <section class="panel ask" aria-labelledby="ask-title">
            <h2 id="ask-title">Your claims officer asked</h2>
            <blockquote>{{ r.question }}</blockquote>
            <form (ngSubmit)="reply(r.id!)">
              <div class="field">
                <label for="reply-{{ r.id }}">Your reply</label>
                <textarea id="reply-{{ r.id }}" name="reply" rows="4" [(ngModel)]="replyText" required></textarea>
              </div>
              <div class="actions">
                <button class="btn btn--primary" type="submit" [disabled]="busy() || !replyText.trim()">
                  {{ busy() ? 'Sending…' : 'Send reply' }}
                </button>
              </div>
            </form>
          </section>
        }

        <section class="panel" aria-labelledby="details-title">
          <h2 id="details-title">What you told us</h2>
          <dl class="facts">
            <div><dt>Incident date</dt><dd>{{ date(c.incidentDate) }}</dd></div>
            <div><dt>Policy</dt><dd class="num">{{ c.policyNumber }}</dd></div>
            <div><dt>Your estimate</dt><dd class="num">{{ money(c.estimatedLoss, c.currency) }}</dd></div>
            @if (c.settledAmount != null) {
              <div><dt>Approved amount</dt><dd class="num strong">{{ money(c.settledAmount, c.currency) }}</dd></div>
            }
          </dl>
          <p class="desc">{{ c.description }}</p>
        </section>

        @if (answeredRequests().length) {
          <section class="panel" aria-labelledby="qa-title">
            <h2 id="qa-title">Questions and answers</h2>
            @for (r of answeredRequests(); track r.id) {
              <div class="qa">
                <p class="q">{{ r.question }}</p>
                <p class="a">{{ r.response }}</p>
              </div>
            }
          </section>
        }

        <section class="panel" aria-labelledby="history-title">
          <h2 id="history-title">History</h2>
          <app-timeline [entries]="c.timeline" />
        </section>
      }
    </div>
  `,
  styles: `
    .back { display: inline-block; margin-bottom: var(--space-4); }
    .back::before { content: '‹ '; }
    .notice { margin-bottom: var(--space-4); }
    .head { margin-bottom: var(--space-4); }
    .head h1 { font-size: var(--text-2xl); }
    .progress { padding: var(--space-6) var(--space-5) var(--space-5); }
    .next { margin-top: var(--space-5); padding-top: var(--space-4); border-top: 1px solid var(--line); font-size: var(--text-lg); max-width: 60ch; }
    .ask { border-color: var(--attention-line); background: #fffbf3; }
    blockquote { margin: 0 0 var(--space-4); padding: var(--space-3) var(--space-4); border-left: 4px solid var(--attention); background: var(--surface); font-size: var(--text-lg); }
    .actions { display: flex; justify-content: flex-end; margin-top: var(--space-3); }
    .facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--space-4); margin: 0 0 var(--space-4); }
    dt { font-size: var(--text-sm); color: var(--muted); }
    dd { margin: 0; font-weight: 600; }
    .strong { color: var(--primary); }
    .desc { white-space: pre-line; max-width: 70ch; }
    .qa + .qa { margin-top: var(--space-3); padding-top: var(--space-3); border-top: 1px solid var(--line); }
    .q { font-weight: 600; }
    .a { color: var(--muted); white-space: pre-line; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
})
export class ClaimantClaim {
  private api = inject(ClaimsApi);

  /** Bound from the route (:id) and query string (?submitted=1). */
  readonly id = input.required<string>();
  readonly submittedParam = input<string | undefined>(undefined, { alias: 'submitted' });

  readonly claim = signal<ClaimDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  replyText = '';

  readonly submitted = computed(() => this.submittedParam() === '1' && this.claim()?.status === 'SUBMITTED');
  readonly openRequests = computed(() => (this.claim()?.infoRequests ?? []).filter(r => r.open));
  readonly answeredRequests = computed(() => (this.claim()?.infoRequests ?? []).filter(r => !r.open));

  readonly nextStep = computed(() => {
    const c = this.claim();
    if (!c) return '';
    const paid = money(c.settledAmount, c.currency);
    switch (c.status) {
      case 'SUBMITTED': return 'We’ve received your claim. A claims officer will pick it up and you’ll see their progress here.';
      case 'ASSIGNED': return `${c.assignedHandlerName ?? 'A claims officer'} has your claim and will start the assessment.`;
      case 'UNDER_ASSESSMENT': return 'Your claims officer is reviewing the damage and your cover. If they need anything from you, it will appear here.';
      case 'AWAITING_INFO': return 'Your claims officer needs more information before they can continue. Reply below.';
      case 'APPROVED': return `Your claim is approved for ${paid}. Payment is being arranged.`;
      case 'SETTLED': return `We’ve paid ${paid}. This claim is closed.`;
      case 'REJECTED': return `Your claim wasn’t approved. Reason: ${c.decisionReason}`;
      default: return '';
    }
  });

  constructor() {
    effect(() => this.load(this.id()));
    poll(() => this.load(this.id()), 10000);
  }

  load(id: string): void {
    this.api.get(id).subscribe({
      next: c => { this.claim.set(c); this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  reply(requestId: string): void {
    const text = this.replyText.trim();
    if (!text) return;
    this.busy.set(true);
    this.api.respond(this.id(), requestId, text).subscribe({
      next: c => { this.claim.set(c); this.replyText = ''; this.busy.set(false); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  money = money;
  date = shortDate;
}
