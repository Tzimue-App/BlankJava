import {
  HttpErrorResponse,
  HttpEvent,
  HttpInterceptorFn,
  HttpRequest,
  HttpHandlerFn,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { AuthSession } from './auth-session';

const API_PREFIX = '/api/';
/** Session endpoints: they authenticate with the refresh cookie, never with the access token. */
const AUTH_PREFIX = '/api/auth/';

/**
 * Authenticates calls to our API with the in-memory access token. On a `401`, renews the session
 * once ({@link AuthSession.refresh}, shared by concurrent requests) and retries the request once;
 * if the refresh or the retry fails, ends the session and sends the user to `/login` with the
 * current URL as `returnUrl`.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API_PREFIX) || request.url.startsWith(AUTH_PREFIX)) {
    return next(request);
  }
  const session = inject(AuthSession);
  const router = inject(Router);
  const sentToken = session.accessToken();

  const loggedOut = (error: unknown): Observable<never> => {
    session.end();
    void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
    return throwError(() => error);
  };

  return send(request, next, sentToken).pipe(
    catchError((error: unknown) => {
      if (!isUnauthorized(error)) {
        return throwError(() => error);
      }
      // Another request renewed the session while this one was in flight: just retry
      const current = session.accessToken();
      const renewed =
        current !== null && current !== sentToken ? Promise.resolve(true) : session.refresh();
      return from(renewed).pipe(
        switchMap((ok) =>
          ok
            ? send(request, next, session.accessToken()).pipe(
                catchError((retryError: unknown) =>
                  isUnauthorized(retryError) ? loggedOut(retryError) : throwError(() => retryError),
                ),
              )
            : loggedOut(error),
        ),
      );
    }),
  );
};

function send(
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
  token: string | null,
): Observable<HttpEvent<unknown>> {
  return next(
    token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request,
  );
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}
