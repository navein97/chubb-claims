import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClaimsApi } from '../api/claims.service';
import { ClaimType, Market } from '../api/models';
import { errorMessage, MARKETS } from '../core/format';

const CURRENCY: Record<Market, string> = { SG: 'SGD', MY: 'MYR', HK: 'HKD', TH: 'THB', ID: 'IDR', PH: 'PHP' };

@Component({
  selector: 'app-new-claim',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="page page--narrow">
      <div class="page-head">
        <div>
          <h1>Report an incident</h1>
          <p>Tell us what happened. A claims officer will review it and may ask for more details.</p>
        </div>
      </div>

      <form class="panel" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }

        <fieldset class="types">
          <legend>What is the claim for?</legend>
          @for (t of types; track t.value) {
            <label class="type" [class.type--on]="form.controls.type.value === t.value">
              <input type="radio" formControlName="type" [value]="t.value" />
              <span class="type-name">{{ t.label }}</span>
              <span class="small muted">{{ t.hint }}</span>
            </label>
          }
        </fieldset>

        <div class="form-grid">
          <div class="field">
            <label for="market">Where is your policy?</label>
            <select id="market" formControlName="market">
              @for (m of markets; track m.code) { <option [value]="m.code">{{ m.name }}</option> }
            </select>
          </div>

          <div class="field">
            <label for="policy">Policy number</label>
            <input id="policy" formControlName="policyNumber" autocomplete="off" placeholder="e.g. POL-MY-10293" />
            @if (showError('policyNumber')) { <span class="error">Enter your policy number.</span> }
          </div>

          <div class="field">
            <label for="date">When did it happen?</label>
            <input id="date" type="date" formControlName="incidentDate" [max]="today" />
            @if (showError('incidentDate')) { <span class="error">Enter a date that isn't in the future.</span> }
          </div>

          <div class="field">
            <label for="loss">Estimated cost ({{ currency() }})</label>
            <input id="loss" type="number" inputmode="decimal" min="0" step="0.01" formControlName="estimatedLoss" />
            <span class="hint">A rough figure is fine. Your claims officer will confirm it.</span>
            @if (showError('estimatedLoss')) { <span class="error">Enter an amount of zero or more.</span> }
          </div>

          <div class="field span-2">
            <label for="desc">What happened?</label>
            <textarea id="desc" formControlName="description" rows="5"
              placeholder="Where it happened, what was damaged, anyone else involved."></textarea>
            @if (showError('description')) { <span class="error">Describe what happened.</span> }
          </div>
        </div>

        <div class="actions">
          <a class="btn btn--quiet" routerLink="/claimant">Cancel</a>
          <button class="btn btn--primary" type="submit" [disabled]="busy()">
            {{ busy() ? 'Submitting…' : 'Submit claim' }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: `
    fieldset { border: 0; margin: 0 0 var(--space-5); padding: 0; }
    legend { font-weight: 600; font-size: var(--text-sm); margin-bottom: var(--space-2); }
    .types { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); }
    .type {
      display: flex; flex-direction: column; gap: 2px;
      padding: var(--space-4);
      border: 1px solid var(--line-strong);
      border-radius: var(--radius);
      cursor: pointer;
      position: relative;
    }
    .type input { position: absolute; opacity: 0; pointer-events: none; }
    .type:has(input:focus-visible) { outline: 3px solid var(--focus); outline-offset: 2px; }
    .type--on { border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); background: #f5faf8; }
    .type-name { font-weight: 600; }
    .notice { margin-bottom: var(--space-4); }
    .actions { display: flex; justify-content: flex-end; gap: var(--space-2); margin-top: var(--space-5); }
  `,
})
export class NewClaim {
  private api = inject(ClaimsApi);
  private router = inject(Router);
  private fb = inject(NonNullableFormBuilder);

  readonly markets = MARKETS;
  readonly today = new Date().toISOString().slice(0, 10);
  readonly types: { value: ClaimType; label: string; hint: string }[] = [
    { value: 'MOTOR', label: 'Vehicle', hint: 'Accident, theft or damage to your car' },
    { value: 'PROPERTY', label: 'Home or property', hint: 'Fire, flood, break-in or other damage' },
  ];

  readonly form = this.fb.group({
    type: this.fb.control<ClaimType>('MOTOR'),
    market: this.fb.control<Market>('MY'),
    policyNumber: this.fb.control('', [Validators.required, Validators.maxLength(60)]),
    incidentDate: this.fb.control(this.today, [Validators.required]),
    estimatedLoss: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    description: this.fb.control('', [Validators.required, Validators.maxLength(4000)]),
  });

  private market = toSignal(this.form.controls.market.valueChanges, { initialValue: this.form.controls.market.value });
  readonly currency = computed(() => CURRENCY[this.market()]);

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  private attempted = signal(false);

  showError(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    const futureDate = name === 'incidentDate' && c.value && String(c.value) > this.today;
    return (c.invalid || !!futureDate) && (c.touched || this.attempted());
  }

  submit(): void {
    this.attempted.set(true);
    const v = this.form.getRawValue();
    if (this.form.invalid || v.incidentDate > this.today) return;

    this.busy.set(true);
    this.error.set(null);
    this.api.submit({
      type: v.type,
      market: v.market,
      policyNumber: v.policyNumber.trim(),
      incidentDate: v.incidentDate,
      estimatedLoss: Number(v.estimatedLoss),
      description: v.description.trim(),
    }).subscribe({
      next: claim => this.router.navigate(['/claimant', claim.id], { queryParams: { submitted: 1 } }),
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }
}
