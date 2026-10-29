import { Component, input } from '@angular/core';
import { TimelineEntry } from '../api/models';
import { dateTime } from '../core/format';

/** Newest first. Staff see who acted; claimants get the backend's filtered, name-free version. */
@Component({
  selector: 'app-timeline',
  template: `
    <ol class="tl">
      @for (e of reversed(); track $index) {
        <li>
          <span class="when small muted num">{{ when(e.at) }}</span>
          <span class="what">
            {{ e.note }}
            @if (e.actorName) { <span class="by small muted">{{ e.actorName }}</span> }
          </span>
        </li>
      }
    </ol>
  `,
  styles: `
    .tl { list-style: none; margin: 0; padding: 0; }
    li {
      display: grid;
      grid-template-columns: 120px 1fr;
      gap: var(--space-4);
      padding: var(--space-2) 0;
      border-top: 1px solid var(--line);
    }
    li:first-child { border-top: 0; }
    .what { display: flex; flex-direction: column; }
    @media (max-width: 520px) { li { grid-template-columns: 1fr; gap: 0; } }
  `,
})
export class Timeline {
  readonly entries = input<TimelineEntry[] | undefined>([]);
  reversed = () => [...(this.entries() ?? [])].reverse();
  when = dateTime;
}
