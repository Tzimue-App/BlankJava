import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('App routes', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    // The footer asks the backend for its versions on every page
    TestBed.inject(HttpTestingController).match('/actuator/info');
  });

  it('renders the lazy-loaded home feature inside the shell', async () => {
    await harness.navigateByUrl('/');

    expect(harness.fixture.nativeElement.querySelector('[data-testid="home"]')).not.toBeNull();
    expect(harness.fixture.nativeElement.querySelector('[data-testid="footer"]')).not.toBeNull();
  });

  it('renders the not-found page for an unknown route', async () => {
    await harness.navigateByUrl('/does/not/exist');

    const notFound = harness.fixture.nativeElement.querySelector('[data-testid="not-found"]');
    expect(notFound?.textContent).toContain('Page not found');
    expect(harness.fixture.nativeElement.querySelector('[data-testid="home"]')).toBeNull();
  });
});
