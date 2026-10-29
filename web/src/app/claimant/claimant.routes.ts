import { Routes } from '@angular/router';

export const CLAIMANT_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./my-claims').then(m => m.MyClaims), title: 'Your claims' },
  { path: 'new', loadComponent: () => import('./new-claim').then(m => m.NewClaim), title: 'Report an incident' },
  { path: ':id', loadComponent: () => import('./claimant-claim').then(m => m.ClaimantClaim), title: 'Claim' },
];
