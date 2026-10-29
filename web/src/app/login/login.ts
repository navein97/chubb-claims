import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ClaimsApi } from '../api/claims.service';
import { DemoUser } from '../api/models';
import { AuthService } from '../core/auth';
import { errorMessage } from '../core/format';

/**
 * DEV login: pick a seeded account. Stands in for the real identity provider so the demo
 * can switch between a policyholder, claims officers and a manager.
 */
@Component({
  selector: 'app-login',
  template: `
    <div class="wrap">
      <section class="card" aria-labelledby="login-title">
        <div class="brand">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5l8 3v6.2c0 4.9-3.4 8.6-8 9.8-4.6-1.2-8-4.9-8-9.8V5.5l8-3z"/></svg>
          <span>Claims</span>
        </div>
        <h1 id="login-title">Sign in</h1>
        <p class="muted">Demo environment. Choose an account to continue.</p>

        @if (error()) { <p class="notice notice--error" role="alert">{{ error() }}</p> }

        @for (group of groups(); track group.title) {
          <h2>{{ group.title }}</h2>
          <ul class="accounts">
            @for (u of group.users; track u.username) {
              <li>
                <button type="button" class="account" [disabled]="busy()" (click)="signIn(u.username!)">
                  <span class="initials" aria-hidden="true">{{ initials(u.user?.name) }}</span>
                  <span class="who">
                    <span class="name">{{ u.user?.name }}</span>
                    <span class="desc">{{ describe(u) }}</span>
                  </span>
                </button>
              </li>
            }
          </ul>
        }
      </section>
    </div>
  `,
  styles: `
    .wrap { min-height: 100vh; display: grid; place-items: center; padding: var(--space-5); }
    .card {
      width: 100%;
      max-width: 460px;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius-lg);
      padding: var(--space-6);
    }
    .brand { display: flex; align-items: center; gap: var(--space-2); font-weight: 700; font-size: var(--text-lg); margin-bottom: var(--space-5); }
    .brand svg { width: 24px; height: 24px; fill: var(--primary); }
    h1 { font-size: var(--text-2xl); }
    h1 + p { margin: var(--space-1) 0 var(--space-5); }
    h2 { font-size: var(--text-sm); color: var(--muted); font-weight: 600; margin: var(--space-4) 0 var(--space-2); }
    .notice { margin-bottom: var(--space-3); }
    .accounts { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--space-2); }
    .account {
      width: 100%;
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-3);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: var(--surface);
      font: inherit;
      color: inherit;
      text-align: left;
      cursor: pointer;
    }
    .account:hover { border-color: var(--primary); background: #f7faf9; }
    .initials {
      width: 36px; height: 36px; border-radius: 50%;
      display: grid; place-items: center;
      background: var(--primary-soft); color: var(--primary);
      font-weight: 700; font-size: var(--text-sm);
      flex-shrink: 0;
    }
    .who { display: flex; flex-direction: column; }
    .name { font-weight: 600; }
    .desc { font-size: var(--text-sm); color: var(--muted); }
  `,
})
export class Login {
  private api = inject(ClaimsApi);
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly users = signal<DemoUser[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly groups = computed(() => {
    const all = this.users();
    return [
      { title: 'Policyholders', users: all.filter(u => u.user?.role === 'CLAIMANT') },
      { title: 'Claims team', users: all.filter(u => u.user?.role !== 'CLAIMANT') },
    ].filter(g => g.users.length);
  });

  constructor() {
    this.api.demoUsers().subscribe({
      next: users => this.users.set(users),
      error: err => this.error.set(errorMessage(err)),
    });
  }

  signIn(username: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(username).subscribe({
      next: user => this.router.navigateByUrl(this.auth.homeFor(user)),
      error: err => {
        this.busy.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }

  initials(name?: string): string {
    return (name ?? '?').split(' ').map(p => p[0]).slice(0, 2).join('');
  }

  describe(u: DemoUser): string {
    const user = u.user;
    if (!user) return '';
    if (user.role === 'CLAIMANT') return 'Policyholder';
    if (user.role === 'MANAGER') return 'Claims manager, all teams';
    return `Claims officer, ${user.team}`;
  }
}
