import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthSession } from '../../core/auth/auth-session';
import { sessionFor } from '../../core/auth/auth-session.testing';
import { LOCKS } from '../../core/platform/tokens';
import { Header } from './header';

const LOGOUT = '/api/auth/logout';

describe('Header', () => {
  let fixture: ComponentFixture<Header>;
  let backend: HttpTestingController;
  let session: AuthSession;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCKS, useValue: null },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSession);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  const logoutButton = () =>
    fixture.nativeElement.querySelector(
      '[data-testid="header-logout"]',
    ) as HTMLButtonElement | null;

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
  };

  it('shows Log out only while a user is logged in', async () => {
    expect(logoutButton()).toBeNull();

    session.start(sessionFor('token'));
    await fixture.whenStable();
    expect(logoutButton()?.textContent?.trim()).toBe('Log out');
  });

  it('revokes the session on the backend, forgets it and goes to /login', async () => {
    session.start(sessionFor('token'));
    await fixture.whenStable();

    logoutButton()!.click();
    await fixture.whenStable();
    const request = backend.expectOne(LOGOUT);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();

    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(logoutButton()).toBeNull();
  });

  it('logs out locally even when the backend cannot be reached', async () => {
    session.start(sessionFor('token'));
    await fixture.whenStable();

    logoutButton()!.click();
    backend.expectOne(LOGOUT).error(new ProgressEvent('error'));
    await settle();

    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
