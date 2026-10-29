import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, Observable, tap, throwError } from 'rxjs';
import { ClaimsApi } from '../api/claims.service';
import { Actor } from '../api/models';

interface Session {
  token: string;
  user: Actor;
}

const STORAGE_KEY = 'claims.session';

/**
 * DEV session: the token is the user id issued by the backend's dev login.
 * Swapping to OIDC changes this service and the interceptor only.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ClaimsApi);
  private session = signal<Session | null>(readSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isStaff = computed(() => {
    const role = this.user()?.role;
    return role === 'HANDLER' || role === 'MANAGER';
  });
  readonly isManager = computed(() => this.user()?.role === 'MANAGER');

  login(username: string): Observable<Actor> {
    return this.api.login(username).pipe(
      map(res => ({ token: res.token!, user: res.user! })),
      tap(s => {
        this.session.set(s);
        try { localStorage.setItem(STORAGE_KEY, JSON.stringify(s)); } catch { /* private mode */ }
      }),
      map(s => s.user),
    );
  }

  logout(): void {
    this.session.set(null);
    try { localStorage.removeItem(STORAGE_KEY); } catch { /* ignore */ }
  }

  /** Where a user lands after login: claimants and staff live in separate areas. */
  homeFor(user: Actor | null): string {
    if (!user) return '/login';
    return user.role === 'CLAIMANT' ? '/claimant' : '/staff';
  }
}

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) as Session : null;
  } catch {
    return null;
  }
}

/** Adds the bearer token; a 401 means the session is gone, so send the user back to login. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const authed = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !req.url.includes('/api/auth/')) {
        auth.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};

/** Role guards: one Angular app, two areas. The backend enforces the same rules again. */
export const claimantGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const user = auth.user();
  if (user?.role === 'CLAIMANT') return true;
  return inject(Router).parseUrl(auth.homeFor(user));
};

export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isStaff()) return true;
  return inject(Router).parseUrl(auth.homeFor(auth.user()));
};

export const homeRedirect: CanActivateFn = () => {
  const auth = inject(AuthService);
  return inject(Router).parseUrl(auth.homeFor(auth.user()));
};
