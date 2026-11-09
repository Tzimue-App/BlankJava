import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthSession } from '../../../core/auth/auth-session';
import { sessionFor } from '../../../core/auth/auth-session.testing';
import { LOCKS } from '../../../core/platform/tokens';
import { RegisterPage } from './register.page';

const REGISTER = '/api/auth/register';
const PASSWORD = 'tangerine-river-boat';

describe('RegisterPage', () => {
  let fixture: ComponentFixture<RegisterPage>;
  let page: RegisterPage;
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
    fixture = TestBed.createComponent(RegisterPage);
    page = fixture.componentInstance;
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

  async function fillValid(email = 'Jane@Example.com'): Promise<void> {
    type('register-email', email);
    type('register-display-name', 'Jane');
    type('register-password', PASSWORD);
    type('register-confirmation', PASSWORD);
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    (element('register-submit') as HTMLButtonElement).click();
    await fixture.whenStable();
  }

  const submitDisabled = () => (element('register-submit') as HTMLButtonElement).disabled;

  /** Lets the HTTP response reach the awaiting page, then renders. */
  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
  }

  it('labels every field and offers the login link', () => {
    const labels = [...fixture.nativeElement.querySelectorAll('mat-label')].map((label) =>
      (label as HTMLElement).textContent?.trim(),
    );
    expect(labels).toEqual(['E-mail address', 'Display name', 'Password', 'Repeat the password']);
    expect(element('register-email')?.getAttribute('autocomplete')).toBe('email');
    expect(element('register-display-name')?.getAttribute('autocomplete')).toBe('nickname');
    expect(element('register-password')?.getAttribute('autocomplete')).toBe('new-password');
    expect(element('register-login-link')?.getAttribute('href')).toBe('/login');
  });

  it('keeps the button disabled and explains each invalid field', async () => {
    type('register-email', 'jane@localhost');
    type('register-display-name', 'J');
    type('register-password', 'a'.repeat(14));
    type('register-confirmation', 'something else');
    await fixture.whenStable();

    expect(submitDisabled()).toBe(true);
    expect(element('register-email-error')?.textContent).toContain('valid e-mail address');
    expect(element('register-display-name-error')?.textContent).toContain('at least 2');
    expect(element('register-password-error')?.textContent).toContain('at least 15');
    expect(element('register-confirmation-error')?.textContent).toContain('do not match');
  });

  it('counts the password like the backend and accepts 14 characters that NFKC makes 16', async () => {
    await fillValid();
    type('register-password', 'a'.repeat(13) + 'ﬃ');
    type('register-confirmation', 'a'.repeat(13) + 'ﬃ');
    await fixture.whenStable();

    expect(element('register-password-count')?.textContent?.trim()).toBe('16 / 64');
    expect(element('register-password-error')).toBeNull();
    expect(submitDisabled()).toBe(false);
  });

  it('shows and hides the password', async () => {
    const password = element('register-password') as HTMLInputElement;
    expect(password.type).toBe('password');

    element('register-password-toggle')?.click();
    await fixture.whenStable();
    expect(password.type).toBe('text');
    expect(element('register-password-toggle')?.getAttribute('aria-pressed')).toBe('true');

    element('register-password-toggle')?.click();
    await fixture.whenStable();
    expect(password.type).toBe('password');
  });

  it('sends the fields as typed (the backend normalises them), opens the session and goes to /me', async () => {
    await fillValid();
    await submit();

    const request = backend.expectOne(REGISTER);
    expect(request.request.body).toEqual({
      email: 'Jane@Example.com',
      displayName: 'Jane',
      password: PASSWORD,
    });
    expect(submitDisabled()).toBe(true);
    request.flush(sessionFor('token-1'), { status: 201, statusText: 'Created' });
    await settle();

    expect(TestBed.inject(AuthSession).accessToken()).toBe('token-1');
    expect(navigate).toHaveBeenCalledWith('/me');
  });

  it('goes back to a safe returnUrl, and ignores another origin', async () => {
    fixture.componentRef.setInput('returnUrl', '/me?tab=profile');
    await fillValid();
    await submit();
    backend.expectOne(REGISTER).flush(sessionFor('token-1'));
    await settle();
    expect(navigate).toHaveBeenLastCalledWith('/me?tab=profile');

    fixture.componentRef.setInput('returnUrl', '//evil.example');
    await submit();
    backend.expectOne(REGISTER).flush(sessionFor('token-2'));
    await settle();
    expect(navigate).toHaveBeenLastCalledWith('/me');
  });

  it('shows "already registered" under the e-mail on a 409, until the e-mail changes', async () => {
    await fillValid();
    await submit();
    backend
      .expectOne(REGISTER)
      .flush(
        { type: 'about:blank', title: 'Conflict', status: 409, code: 'email_taken', traceId: 't' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle();

    expect(element('register-email-error')?.textContent).toContain('already registered');
    expect(submitDisabled()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();

    type('register-email', 'other@example.com');
    await fixture.whenStable();
    expect(element('register-email-error')).toBeNull();
    expect(submitDisabled()).toBe(false);
  });

  it('shows each server field error under its field, the breached password included', async () => {
    await fillValid();
    await submit();
    backend.expectOne(REGISTER).flush(
      {
        type: 'about:blank',
        title: 'Bad Request',
        status: 400,
        traceId: 't',
        errors: [
          { field: 'password', code: 'compromised_password', message: 'x' },
          { field: 'displayName', code: 'invalid_format', message: 'y' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(element('register-password-error')?.textContent).toContain('known data breaches');
    expect(element('register-display-name-error')?.textContent).toContain('not allowed');
    expect(element('register-error-banner')).toBeNull();
  });

  it('shows a banner with the trace id for any other failure', async () => {
    await fillValid();
    await submit();
    backend.expectOne(REGISTER).flush(
      {
        type: 'about:blank',
        title: 'Error',
        status: 500,
        traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
      },
      { status: 500, statusText: 'Server Error' },
    );
    await settle();

    expect(element('register-error-banner')?.getAttribute('role')).toBe('alert');
    expect(element('register-trace-id')?.textContent).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
    expect(submitDisabled()).toBe(false);
  });

  it('shows the banner without trace id when the backend is unreachable', async () => {
    await fillValid();
    await submit();
    backend.expectOne(REGISTER).error(new ProgressEvent('error'));
    await settle();

    expect(element('register-error-banner')).not.toBeNull();
    expect(element('register-trace-id')).toBeNull();
  });

  it('uses the banner for a 400 about a field the form does not have', async () => {
    await fillValid();
    await submit();
    backend.expectOne(REGISTER).flush(
      {
        status: 400,
        title: 'Bad Request',
        type: 'about:blank',
        traceId: 'abc',
        errors: [{ field: 'body', code: 'required' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(element('register-trace-id')?.textContent).toBe('abc');
  });

  it('does not send an invalid form, and reveals its errors', async () => {
    await page.submit();
    await fixture.whenStable();

    backend.expectNone(REGISTER);
    expect(element('register-email-error')?.textContent).toContain('Enter your e-mail address');
  });
});
