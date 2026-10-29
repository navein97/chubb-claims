import { HttpErrorResponse } from '@angular/common/http';
import { age, errorMessage, money } from './format';

describe('format helpers', () => {
  it('formats money in the claim currency', () => {
    // Intl separates code and amount with a non-breaking space
    expect(money(3500, 'MYR').replace(/\s/g, ' ')).toBe('MYR 3,500.00');
    expect(money(undefined, 'MYR')).toBe('—');
  });

  it('describes ages relative to now', () => {
    const now = Date.parse('2026-10-29T12:00:00Z');
    expect(age('2026-10-29T11:59:30Z', now)).toBe('just now');
    expect(age('2026-10-29T09:00:00Z', now)).toBe('3 h');
    expect(age('2026-10-25T12:00:00Z', now)).toBe('4 days');
  });

  it('surfaces the backend problem detail', () => {
    const err = new HttpErrorResponse({ status: 409, error: { detail: 'Claim already picked up by Raj Kumar' } });
    expect(errorMessage(err)).toBe('Claim already picked up by Raj Kumar');
  });

  it('explains an unreachable server', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Can’t reach the server');
  });
});
