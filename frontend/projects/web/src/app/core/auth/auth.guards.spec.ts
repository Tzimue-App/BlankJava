import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { AuthSession } from './auth-session';
import { sessionFor } from './auth-session.testing';
import { anonymousGuard, authGuard } from './auth.guards';

describe('auth guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/me?tab=profile' } as RouterStateSnapshot;
  let session: AuthSession;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    session = TestBed.inject(AuthSession);
  });

  const run = (guard: typeof authGuard) => TestBed.runInInjectionContext(() => guard(route, state));
  const serialize = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('authGuard lets a logged-in user through', () => {
    session.start(sessionFor('token'));

    expect(run(authGuard)).toBe(true);
  });

  it('authGuard sends an anonymous visitor to /login with the requested URL', () => {
    const result = run(authGuard);

    expect(result).toBeInstanceOf(UrlTree);
    expect(serialize(result)).toBe('/login?returnUrl=%2Fme%3Ftab%3Dprofile');
  });

  it('anonymousGuard lets an anonymous visitor through', () => {
    expect(run(anonymousGuard)).toBe(true);
  });

  it('anonymousGuard sends a logged-in user to /me', () => {
    session.start(sessionFor('token'));

    expect(serialize(run(anonymousGuard))).toBe('/me');
  });
});
