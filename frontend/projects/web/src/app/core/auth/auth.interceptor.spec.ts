import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LOCKS } from '../platform/tokens';
import { AuthSession } from './auth-session';
import { sessionFor } from './auth-session.testing';
import { authInterceptor } from './auth.interceptor';

const REFRESH = '/api/auth/refresh';
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

/** Lets pending promises and the HTTP client settle. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: AuthSession;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCKS, useValue: null },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSession);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/me?tab=profile');
    navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  it('adds the access token to API calls only, never to the session endpoints', () => {
    session.start(sessionFor('token-1'));

    http.get('/api/users/me').subscribe();
    http.post('/api/auth/logout', null).subscribe();
    http.get('/config.json').subscribe();

    expect(backend.expectOne('/api/users/me').request.headers.get('Authorization')).toBe(
      'Bearer token-1',
    );
    expect(backend.expectOne('/api/auth/logout').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('/config.json').request.headers.has('Authorization')).toBe(false);
  });

  it('sends no Authorization header while anonymous', () => {
    http.post('/api/client-logs', []).subscribe();

    expect(backend.expectOne('/api/client-logs').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once for three concurrent 401s, then retries each request once', async () => {
    session.start(sessionFor('expired'));
    const results: string[] = [];
    for (const path of ['/api/a', '/api/b', '/api/c']) {
      http.get<string>(path).subscribe((body) => results.push(body));
    }
    for (const path of ['/api/a', '/api/b', '/api/c']) {
      backend.expectOne(path).flush(null, UNAUTHORIZED);
    }
    await settle();

    backend.expectOne(REFRESH).flush(sessionFor('fresh'));
    await settle();

    for (const path of ['/api/a', '/api/b', '/api/c']) {
      const retry = backend.expectOne(path);
      expect(retry.request.headers.get('Authorization')).toBe('Bearer fresh');
      retry.flush(path);
    }
    expect(results.sort()).toEqual(['/api/a', '/api/b', '/api/c']);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('retries without refreshing when another request already renewed the token', async () => {
    session.start(sessionFor('old'));
    let body: unknown;
    http.get('/api/a').subscribe((value) => (body = value));
    session.start(sessionFor('new'));
    backend.expectOne('/api/a').flush(null, UNAUTHORIZED);
    await settle();

    const retry = backend.expectOne('/api/a');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
    retry.flush('ok');
    expect(body).toBe('ok');
  });

  it('ends the session and goes to /login with the current URL when the refresh fails', async () => {
    session.start(sessionFor('expired'));
    let failure: unknown;
    http.get('/api/a').subscribe({ error: (error: unknown) => (failure = error) });
    backend.expectOne('/api/a').flush(null, UNAUTHORIZED);
    await settle();

    backend.expectOne(REFRESH).flush(null, UNAUTHORIZED);
    await settle();

    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/me?tab=profile' },
    });
    expect(failure).toMatchObject({ status: 401 });
  });

  it('ends the session when the retried request is refused again', async () => {
    session.start(sessionFor('expired'));
    http.get('/api/a').subscribe({ error: () => undefined });
    backend.expectOne('/api/a').flush(null, UNAUTHORIZED);
    await settle();
    backend.expectOne(REFRESH).flush(sessionFor('fresh'));
    await settle();

    backend.expectOne('/api/a').flush(null, UNAUTHORIZED);

    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledOnce();
  });

  it('passes other errors through without refreshing', async () => {
    session.start(sessionFor('token-1'));
    let failure: unknown;
    http.get('/api/a').subscribe({ error: (error: unknown) => (failure = error) });
    backend.expectOne('/api/a').flush(null, { status: 403, statusText: 'Forbidden' });
    await settle();

    backend.expectNone(REFRESH);
    expect(failure).toMatchObject({ status: 403 });
    expect(session.isAuthenticated()).toBe(true);
  });

  it('passes a 500 of the retry through without ending the session', async () => {
    session.start(sessionFor('expired'));
    let failure: unknown;
    http.get('/api/a').subscribe({ error: (error: unknown) => (failure = error) });
    backend.expectOne('/api/a').flush(null, UNAUTHORIZED);
    await settle();
    backend.expectOne(REFRESH).flush(sessionFor('fresh'));
    await settle();

    backend.expectOne('/api/a').flush(null, { status: 500, statusText: 'Server Error' });

    expect(failure).toMatchObject({ status: 500 });
    expect(session.isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });
});
