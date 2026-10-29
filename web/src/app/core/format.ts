import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, inject } from '@angular/core';
import { ClaimStatus, Problem } from '../api/models';

/** Plain-language status for claimants: what is happening, not the internal state name. */
export const CLAIMANT_STATUS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Received',
  ASSIGNED: 'With a claims officer',
  UNDER_ASSESSMENT: 'Being assessed',
  AWAITING_INFO: 'We need more information',
  APPROVED: 'Approved, payment on the way',
  SETTLED: 'Paid',
  REJECTED: 'Not approved',
};

export const STAFF_STATUS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Unassigned',
  ASSIGNED: 'Assigned',
  UNDER_ASSESSMENT: 'Assessing',
  AWAITING_INFO: 'Awaiting info',
  APPROVED: 'Approved',
  SETTLED: 'Settled',
  REJECTED: 'Rejected',
};

export const MARKETS: { code: 'SG' | 'MY' | 'HK' | 'TH' | 'ID' | 'PH'; name: string }[] = [
  { code: 'MY', name: 'Malaysia' },
  { code: 'SG', name: 'Singapore' },
  { code: 'HK', name: 'Hong Kong' },
  { code: 'TH', name: 'Thailand' },
  { code: 'ID', name: 'Indonesia' },
  { code: 'PH', name: 'Philippines' },
];

export function money(amount: number | null | undefined, currency: string | null | undefined): string {
  if (amount == null || !currency) return '—';
  return new Intl.NumberFormat('en', { style: 'currency', currency, maximumFractionDigits: 2 }).format(amount);
}

export function shortDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(iso));
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso));
}

/** "3 days", "5 hours", "just now" — for queue ageing. */
export function age(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return '—';
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  if (minutes < 2) return 'just now';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours} h`;
  return `${Math.round(hours / 24)} days`;
}

/** Turn a backend problem response into a sentence the user can act on. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Can’t reach the server. Check that the backend is running, then try again.';
    const problem = err.error as Problem | null;
    if (problem?.detail) return problem.detail;
    return `Request failed (${err.status}).`;
  }
  return 'Something went wrong. Try again.';
}

/**
 * Re-run `fn` every `ms` while the component is alive and the tab is visible.
 * Claims move asynchronously (other staff, Kafka-fed dashboards), so open screens refresh themselves.
 */
export function poll(fn: () => void, ms: number): void {
  const destroyRef = inject(DestroyRef);
  const id = setInterval(() => {
    if (document.visibilityState === 'visible') fn();
  }, ms);
  destroyRef.onDestroy(() => clearInterval(id));
}
