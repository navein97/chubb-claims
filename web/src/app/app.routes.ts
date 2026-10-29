import { Routes } from '@angular/router';
import { claimantGuard, homeRedirect, staffGuard } from './core/auth';
import { Login } from './login/login';
import { Shell } from './shell/shell';

/**
 * One app, two areas. Each area is lazy-loaded and guarded by role, so a claimant's
 * bundle never contains the staff screens and vice versa.
 */
export const routes: Routes = [
  { path: 'login', component: Login, title: 'Sign in' },
  {
    path: 'claimant',
    component: Shell,
    canActivate: [claimantGuard],
    loadChildren: () => import('./claimant/claimant.routes').then(m => m.CLAIMANT_ROUTES),
  },
  {
    path: 'staff',
    component: Shell,
    canActivate: [staffGuard],
    loadChildren: () => import('./staff/staff.routes').then(m => m.STAFF_ROUTES),
  },
  { path: '**', canActivate: [homeRedirect], children: [] },
];
