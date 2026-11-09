import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSession } from './auth-session';

/** Pages for logged-in users: an anonymous visitor goes to `/login`, then back here. */
export const authGuard: CanActivateFn = (_route, state) =>
  inject(AuthSession).isAuthenticated() ||
  inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });

/** Pages for anonymous visitors (login, register): a logged-in user goes to `/me`. */
export const anonymousGuard: CanActivateFn = () =>
  !inject(AuthSession).isAuthenticated() || inject(Router).createUrlTree(['/me']);
