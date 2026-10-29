import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth';

/** Frame for signed-in users. Navigation depends on role; the routes themselves are guarded. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="bar">
      <div class="bar-inner">
        <a class="brand" [routerLink]="home()">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5l8 3v6.2c0 4.9-3.4 8.6-8 9.8-4.6-1.2-8-4.9-8-9.8V5.5l8-3z"/></svg>
          <span>Claims</span>
        </a>

        @if (auth.isStaff()) {
          <nav aria-label="Main">
            <a routerLink="/staff/queue" routerLinkActive="active">Queue</a>
            <a routerLink="/staff/mine" routerLinkActive="active">My claims</a>
            <a routerLink="/staff/dashboard" routerLinkActive="active">{{ auth.isManager() ? 'Dashboard' : 'Team' }}</a>
          </nav>
        } @else {
          <nav aria-label="Main">
            <a routerLink="/claimant" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">My claims</a>
            <a routerLink="/claimant/new" routerLinkActive="active">Report an incident</a>
          </nav>
        }

        <div class="who">
          <span class="name">{{ auth.user()?.name }}</span>
          <span class="role">{{ roleLabel() }}</span>
          <button type="button" class="btn btn--quiet" (click)="signOut()">Sign out</button>
        </div>
      </div>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .bar { background: var(--ink); color: #fff; }
    .bar-inner {
      max-width: 1120px;
      margin: 0 auto;
      padding: 0 var(--space-5);
      min-height: 60px;
      display: flex;
      align-items: center;
      gap: var(--space-6);
    }
    .brand { display: flex; align-items: center; gap: var(--space-2); color: #fff; text-decoration: none; font-weight: 700; font-size: var(--text-lg); }
    .brand svg { width: 22px; height: 22px; fill: #7fc4ae; }
    .brand:hover { color: #fff; }
    nav { display: flex; gap: var(--space-1); flex: 1; }
    nav a {
      color: #c6d1d6;
      text-decoration: none;
      font-weight: 500;
      padding: var(--space-2) var(--space-3);
      border-radius: var(--radius);
    }
    nav a:hover { color: #fff; background: rgba(255,255,255,0.06); }
    nav a.active { color: #fff; background: rgba(255,255,255,0.12); }
    .who { display: flex; align-items: center; gap: var(--space-3); font-size: var(--text-sm); }
    .name { font-weight: 600; }
    .role { color: #9fb0b8; }
    .who .btn--quiet { color: #c6d1d6; min-height: 32px; }
    .who .btn--quiet:hover { color: #fff; }
    @media (max-width: 760px) {
      .bar-inner { flex-wrap: wrap; gap: var(--space-2) var(--space-4); padding: var(--space-2) var(--space-4); }
      nav { order: 3; flex-basis: 100%; overflow-x: auto; }
      .role { display: none; }
      .who { margin-left: auto; }
    }
  `,
})
export class Shell {
  protected auth = inject(AuthService);
  private router = inject(Router);

  readonly home = computed(() => this.auth.homeFor(this.auth.user()));
  readonly roleLabel = computed(() => {
    const u = this.auth.user();
    if (!u) return '';
    if (u.role === 'CLAIMANT') return 'Policyholder';
    return u.role === 'MANAGER' ? 'Claims manager' : `Claims officer, ${u.team}`;
  });

  signOut(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
