import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthSession } from '../../../core/auth/auth-session';
import { sessionFor } from '../../../core/auth/auth-session.testing';
import { LOCKS } from '../../../core/platform/tokens';
import { LoginPage } from './login.page';

const LOGIN = '/api/auth/login';
const INVALID = {
  type: 'about:blank',
  title: 'Unauthorized',
  status: 401,
  detail: 'Invalid e-mail or password.',
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
};

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let backend: HttpTestingController;
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
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  const element = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  function type(testId: string, value: string): void {
    const input = element(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  async function logIn(email: string, password: string): Promise<void> {
    type('login-email', email);
    type('login-password', password);
    await fixture.whenStable();
    (element('login-submit') as HTMLButtonElement).click();
    await fixture.whenStable();
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
  }

  const submitDisabled = () => (element('login-submit') as HTMLButtonElement).disabled;

  it('labels the fields for password managers and links to registration', () => {
    expect(element('login-email')?.getAttribute('autocomplete')).toBe('username');
    expect(element('login-password')?.getAttribute('autocomplete')).toBe('current-password');
    expect(element('login-register-link')?.getAttribute('href')).toBe('/register');
    expect(submitDisabled()).toBe(true);
  });

  it('asks for both fields, but sends any password without checking the policy', async () => {
    type('login-email', '');
    type('login-password', '');
    await fixture.whenStable();
    expect(element('login-email-error')?.textContent).toContain('Enter your e-mail');
    expect(element('login-password-error')?.textContent).toContain('Enter your password');

    await logIn('jane@example.com', 'abc');
    expect(backend.expectOne(LOGIN).request.body).toEqual({
      email: 'jane@example.com',
      password: 'abc',
    });
  });

  it('opens the session and goes to /me', async () => {
    await logIn('Jane@Example.com', 'tangerine-river-boat');
    backend.expectOne(LOGIN).flush(sessionFor('token-1'));
    await settle();

    expect(TestBed.inject(AuthSession).accessToken()).toBe('token-1');
    expect(navigate).toHaveBeenCalledWith('/me');
  });

  it.each([
    ['/me?tab=profile', '/me?tab=profile'],
    ['https://evil.example', '/me'],
    ['//evil.example', '/me'],
    ['/\\evil.example', '/me'],
  ])('with returnUrl %s goes to %s', async (returnUrl, target) => {
    fixture.componentRef.setInput('returnUrl', returnUrl);
    await logIn('jane@example.com', 'tangerine-river-boat');
    backend.expectOne(LOGIN).flush(sessionFor('token-1'));
    await settle();

    expect(navigate).toHaveBeenCalledWith(target);
  });

  it('shows one generic message on a 401, never which field, and clears the password', async () => {
    await logIn('jane@example.com', 'wrong-password-here');
    backend.expectOne(LOGIN).flush(INVALID, { status: 401, statusText: 'Unauthorized' });
    await settle();

    expect(element('login-rejected')?.textContent?.trim()).toBe('Invalid e-mail or password.');
    expect(element('login-rejected')?.getAttribute('role')).toBe('alert');
    expect((element('login-password') as HTMLInputElement).value).toBe('');
    expect((element('login-email') as HTMLInputElement).value).toBe('jane@example.com');
    expect(element('login-error-banner')).toBeNull();
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(AuthSession).isAuthenticated()).toBe(false);
  });

  it('shows a banner with the trace id for any other failure, and clears it on the next try', async () => {
    await logIn('jane@example.com', 'tangerine-river-boat');
    backend
      .expectOne(LOGIN)
      .flush({ ...INVALID, status: 503 }, { status: 503, statusText: 'Unavailable' });
    await settle();

    expect(element('login-trace-id')?.textContent).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
    expect(element('login-rejected')).toBeNull();

    (element('login-submit') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(element('login-error-banner')).toBeNull();
    backend.expectOne(LOGIN).error(new ProgressEvent('error'));
    await settle();
    expect(element('login-error-banner')).not.toBeNull();
    expect(element('login-trace-id')).toBeNull();
  });

  it('shows and hides the password', async () => {
    const password = element('login-password') as HTMLInputElement;
    element('login-password-toggle')?.click();
    await fixture.whenStable();
    expect(password.type).toBe('text');
    element('login-password-toggle')?.click();
    await fixture.whenStable();
    expect(password.type).toBe('password');
  });

  it('does not send an empty form', async () => {
    await fixture.componentInstance.submit();
    backend.expectNone(LOGIN);
  });
});
