import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AuthSession } from './core/auth/auth-session';
import { sessionFor } from './core/auth/auth-session.testing';
import { LoginPage } from './features/accounts/login/login.page';
import { RegisterPage } from './features/accounts/register/register.page';

describe('routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes, withComponentInputBinding()),
      ],
    });
  });

  it('/register shows the registration page to an anonymous visitor, with its returnUrl', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/register?returnUrl=%2Fme');
    const page = harness.fixture.debugElement.query(By.directive(RegisterPage))
      .componentInstance as RegisterPage;

    expect(page.returnUrl()).toBe('/me');
    expect(TestBed.inject(Router).url).toBe('/register?returnUrl=%2Fme');
  });

  it('/register sends a logged-in user to /me', async () => {
    TestBed.inject(AuthSession).start(sessionFor('token'));
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/register');

    expect(TestBed.inject(Router).url).toBe('/me');
  });

  it('/login shows the login page to an anonymous visitor, with its returnUrl', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/login?returnUrl=%2Fme');
    const page = harness.fixture.debugElement.query(By.directive(LoginPage))
      .componentInstance as LoginPage;

    expect(page.returnUrl()).toBe('/me');
  });

  it('/login sends a logged-in user to /me', async () => {
    TestBed.inject(AuthSession).start(sessionFor('token'));
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/login');

    expect(TestBed.inject(Router).url).toBe('/me');
  });
});
