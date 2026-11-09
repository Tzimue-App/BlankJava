import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LOCKS, STORAGE } from '../platform/tokens';
import { AuthSession, REFRESH_LOCK } from './auth-session';
import { sessionFor } from './auth-session.testing';

const REFRESH = '/api/auth/refresh';

/** Lets pending promises and the HTTP client settle. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

/** A LockManager shared by "tabs": grants one holder at a time and records the most at once. */
class FakeLocks {
  names: string[] = [];
  holders = 0;
  mostHolders = 0;
  private tail: Promise<unknown> = Promise.resolve();

  request<T>(name: string, callback: () => Promise<T>): Promise<T> {
    this.names.push(name);
    const run = this.tail.then(async () => {
      this.holders++;
      this.mostHolders = Math.max(this.mostHolders, this.holders);
      try {
        return await callback();
      } finally {
        this.holders--;
      }
    });
    this.tail = run.catch(() => undefined);
    return run;
  }
}

describe('AuthSession', () => {
  let backend: HttpTestingController;
  const storage = { setItem: vi.fn(), getItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn() };

  function configure(locks: FakeLocks | null): AuthSession {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: STORAGE, useValue: storage },
        { provide: LOCKS, useValue: locks },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthSession);
  }

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
  });

  it('starts anonymous, then holds the user and the token until ended', () => {
    const session = configure(null);
    expect(session.isAuthenticated()).toBe(false);
    expect(session.accessToken()).toBeNull();

    session.start(sessionFor('token-1'));
    expect(session.isAuthenticated()).toBe(true);
    expect(session.currentUser()?.displayName).toBe('Jane');
    expect(session.accessToken()).toBe('token-1');

    session.end();
    expect(session.isAuthenticated()).toBe(false);
    expect(session.currentUser()).toBeNull();
    expect(session.accessToken()).toBeNull();
  });

  it('never writes the access token to Web Storage or a cookie', async () => {
    const webStorage = vi.spyOn(Storage.prototype, 'setItem');
    const session = configure(null);
    const document = TestBed.inject(DOCUMENT);

    session.start(sessionFor('token-1'));
    const renewed = session.refresh();
    backend.expectOne(REFRESH).flush(sessionFor('token-2'));
    await renewed;

    expect(session.accessToken()).toBe('token-2');
    expect(storage.setItem).not.toHaveBeenCalled();
    expect(webStorage).not.toHaveBeenCalled();
    expect(document.cookie).not.toContain('token');
  });

  it('restores the session of a valid refresh cookie at startup', async () => {
    const session = configure(null);

    const restored = session.restore();
    const request = backend.expectOne(REFRESH);
    expect(request.request.method).toBe('POST');
    request.flush(sessionFor('token-1'));
    await restored;

    expect(session.currentUser()?.email).toBe('jane@example.com');
    expect(session.accessToken()).toBe('token-1');
  });

  it('starts anonymous without a valid cookie, and the restore does not fail', async () => {
    const session = configure(null);

    const restored = session.restore();
    backend.expectOne(REFRESH).flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    await expect(restored).resolves.toBeUndefined();
    expect(session.isAuthenticated()).toBe(false);
  });

  it('ends the session when a refresh is refused or the backend is unreachable', async () => {
    const session = configure(null);
    session.start(sessionFor('token-1'));

    const refused = session.refresh();
    backend.expectOne(REFRESH).error(new ProgressEvent('error'));

    expect(await refused).toBe(false);
    expect(session.accessToken()).toBeNull();
  });

  it('shares one refresh request between concurrent callers of a tab', async () => {
    const session = configure(null);

    const first = session.refresh();
    const second = session.refresh();
    backend.expectOne(REFRESH).flush(sessionFor('token-2'));

    expect(await Promise.all([first, second])).toEqual([true, true]);
    // The next refresh is a new request
    const third = session.refresh();
    backend.expectOne(REFRESH).flush(sessionFor('token-3'));
    expect(await third).toBe(true);
  });

  it('runs the refreshes of two tabs one after the other inside the Web Lock', async () => {
    const locks = new FakeLocks();
    const tabA = configure(locks);
    const tabB = TestBed.runInInjectionContext(() => new AuthSession());

    const a = tabA.refresh();
    const b = tabB.refresh();
    await settle();

    // Only one tab holds the lock: the other one's request is not sent yet
    backend.expectOne(REFRESH).flush(sessionFor('token-a'));
    await settle();
    backend.expectOne(REFRESH).flush(sessionFor('token-b'));

    expect(await Promise.all([a, b])).toEqual([true, true]);
    expect(tabA.accessToken()).toBe('token-a');
    expect(tabB.accessToken()).toBe('token-b');
    expect(locks.names).toEqual([REFRESH_LOCK, REFRESH_LOCK]);
    expect(locks.mostHolders).toBe(1);
  });

  it('releases the lock when a refresh fails, so the next tab can go', async () => {
    const locks = new FakeLocks();
    const tabA = configure(locks);
    const tabB = TestBed.runInInjectionContext(() => new AuthSession());

    const a = tabA.refresh();
    const b = tabB.refresh();
    await settle();
    backend.expectOne(REFRESH).flush(null, { status: 401, statusText: 'Unauthorized' });
    await settle();
    backend.expectOne(REFRESH).flush(sessionFor('token-b'));

    expect(await Promise.all([a, b])).toEqual([false, true]);
  });

  it('logs out on the backend inside the Web Lock, then forgets the session', async () => {
    const locks = new FakeLocks();
    const session = configure(locks);
    session.start(sessionFor('token-1'));

    const leaving = session.logout();
    await settle();
    backend.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await leaving;

    expect(locks.names).toEqual([REFRESH_LOCK]);
    expect(session.isAuthenticated()).toBe(false);
  });

  it('forgets the session even when the logout call fails', async () => {
    const session = configure(null);
    session.start(sessionFor('token-1'));

    const leaving = session.logout();
    backend.expectOne('/api/auth/logout').error(new ProgressEvent('error'));
    await expect(leaving).resolves.toBeUndefined();

    expect(session.accessToken()).toBeNull();
  });
});
