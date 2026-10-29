import { Component, computed, input } from '@angular/core';
import { ClaimStatus } from '../api/models';
import { CLAIMANT_STATUS, STAFF_STATUS } from '../core/format';

@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [class]="'chip ' + tone()">{{ label() }}</span>`,
})
export class StatusChip {
  readonly status = input.required<ClaimStatus | undefined>();
  readonly audience = input<'claimant' | 'staff'>('staff');

  readonly label = computed(() => {
    const s = this.status();
    if (!s) return '';
    return this.audience() === 'claimant' ? CLAIMANT_STATUS[s] : STAFF_STATUS[s];
  });

  readonly tone = computed(() => {
    switch (this.status()) {
      case 'AWAITING_INFO': return 'chip--attention';
      case 'SUBMITTED': return this.audience() === 'staff' ? 'chip--new' : '';
      case 'APPROVED':
      case 'SETTLED': return 'chip--done';
      case 'REJECTED': return 'chip--danger';
      default: return '';
    }
  });
}
