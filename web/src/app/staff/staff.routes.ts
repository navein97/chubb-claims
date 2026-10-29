import { Routes } from '@angular/router';

export const STAFF_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'queue' },
  { path: 'queue', loadComponent: () => import('./queue').then(m => m.Queue), title: 'Queue' },
  { path: 'mine', loadComponent: () => import('./worklist').then(m => m.Worklist), title: 'Claims' },
  { path: 'claims/:id', loadComponent: () => import('./staff-claim').then(m => m.StaffClaim), title: 'Claim' },
  { path: 'dashboard', loadComponent: () => import('./dashboard').then(m => m.Dashboard), title: 'Dashboard' },
];
