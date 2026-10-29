import { Component, computed, input } from '@angular/core';
import { ClaimStatus } from '../api/models';

interface Stage {
  label: string;
  state: 'done' | 'current' | 'waiting' | 'stopped' | 'todo';
}

const STAGE_OF: Record<ClaimStatus, number> = {
  SUBMITTED: 0,
  ASSIGNED: 1,
  UNDER_ASSESSMENT: 2,
  AWAITING_INFO: 2,
  APPROVED: 3,
  REJECTED: 3,
  SETTLED: 4,
};

/**
 * The claim's journey as a single rail: where it is, what's done, what's next.
 * Amber when the claim is waiting on the claimant; red stop when rejected.
 */
@Component({
  selector: 'app-status-track',
  template: `
    <ol class="track" [class.track--compact]="compact()" [attr.aria-label]="'Claim progress: ' + ariaLabel()">
      @for (stage of stages(); track stage.label; let i = $index) {
        <li class="stage stage--{{ stage.state }}">
          <span class="dot" aria-hidden="true">
            @if (stage.state === 'done') { <svg viewBox="0 0 16 16"><path d="M3.5 8.5l3 3 6-7" /></svg> }
            @if (stage.state === 'stopped') { <svg viewBox="0 0 16 16"><path d="M4.5 4.5l7 7M11.5 4.5l-7 7" /></svg> }
            @if (stage.state === 'waiting') { <span class="bang">!</span> }
          </span>
          <span class="label">{{ stage.label }}</span>
        </li>
      }
    </ol>
  `,
  styles: `
    .track {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(5, minmax(0, 1fr));
      counter-reset: none;
    }
    .stage {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      text-align: center;
      font-size: var(--text-sm);
      color: var(--muted);
    }
    /* the rail: a segment from each stage to the previous one */
    .stage + .stage::before {
      content: '';
      position: absolute;
      top: 13px;
      right: 50%;
      width: 100%;
      height: 3px;
      background: var(--line);
      z-index: 0;
    }
    .stage--done + .stage::before,
    .stage--done + .stage--current::before { background: var(--primary); }
    .stage--done + .stage--waiting::before { background: var(--attention-line); }
    .stage--done + .stage--stopped::before { background: #e1b1a7; }

    .dot {
      position: relative;
      z-index: 1;
      width: 29px;
      height: 29px;
      border-radius: 50%;
      background: var(--surface);
      border: 3px solid var(--line);
      display: grid;
      place-items: center;
    }
    .dot svg { width: 15px; height: 15px; fill: none; stroke: #fff; stroke-width: 2.4; stroke-linecap: round; stroke-linejoin: round; }
    .bang { font-weight: 700; color: #fff; font-size: 14px; line-height: 1; }

    .stage--done .dot { background: var(--primary); border-color: var(--primary); }
    .stage--done .label { color: var(--ink); }
    .stage--current .dot { border-color: var(--primary); box-shadow: 0 0 0 5px var(--primary-soft); }
    .stage--current .label { color: var(--primary); font-weight: 700; }
    .stage--waiting .dot { background: var(--attention); border-color: var(--attention); box-shadow: 0 0 0 5px var(--attention-soft); }
    .stage--waiting .label { color: var(--attention); font-weight: 700; }
    .stage--stopped .dot { background: var(--danger); border-color: var(--danger); }
    .stage--stopped .label { color: var(--danger); font-weight: 700; }

    .track--compact .dot { width: 21px; height: 21px; border-width: 2px; }
    .track--compact .stage + .stage::before { top: 9px; height: 2px; }
    .track--compact .dot svg { width: 11px; height: 11px; }
    .track--compact .bang { font-size: 11px; }
    .track--compact .stage--current .dot, .track--compact .stage--waiting .dot { box-shadow: 0 0 0 3px var(--primary-soft); }

    @media (max-width: 520px) {
      .label { font-size: 11px; }
    }
  `,
})
export class StatusTrack {
  readonly status = input.required<ClaimStatus>();
  readonly compact = input(false);

  readonly stages = computed<Stage[]>(() => {
    const status = this.status();
    const at = STAGE_OF[status];
    const decision = status === 'REJECTED' ? 'Not approved' : status === 'APPROVED' || status === 'SETTLED' ? 'Approved' : 'Decision';
    const labels = ['Received', 'Claims officer', status === 'AWAITING_INFO' ? 'Needs your input' : 'Assessment', decision, 'Paid'];
    return labels.map((label, i) => {
      let state: Stage['state'] = i < at ? 'done' : i === at ? 'current' : 'todo';
      if (i === at && status === 'AWAITING_INFO') state = 'waiting';
      if (i === at && status === 'REJECTED') state = 'stopped';
      if (status === 'SETTLED' && i === 4) state = 'done';
      return { label, state };
    });
  });

  readonly ariaLabel = computed(() => {
    const current = this.stages().find(s => s.state !== 'done' && s.state !== 'todo');
    return current?.label ?? 'Paid';
  });
}
