import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RuntimeConfigService } from '../../core/config/runtime-config';
import { FRONTEND_VERSION } from '../../core/version/frontend-version.generated';
import { Footer } from './footer';

describe('Footer', () => {
  let fixture: ComponentFixture<Footer>;
  let http: HttpTestingController;

  const text = (testId: string) =>
    (fixture.nativeElement as HTMLElement)
      .querySelector(`[data-testid="${testId}"]`)
      ?.textContent?.trim();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Footer],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const config = TestBed.inject(RuntimeConfigService);
    const loading = config.load();
    http.expectOne('/config.json').flush({ release: '0.1.0-b' });
    await loading;

    fixture = TestBed.createComponent(Footer);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('shows the release and the frontend version immediately', () => {
    http.expectOne('/actuator/info');

    expect(text('version-release')).toBe('Release 0.1.0-b');
    expect(text('version-frontend')).toBe(`Frontend ${FRONTEND_VERSION}`);
    expect(text('version-backend-loading')).toBe('Backend …');
  });

  it('shows the backend and API versions from /actuator/info', async () => {
    http.expectOne('/actuator/info').flush({ build: { version: '0.1.0' }, api: '0.1.0' });
    await fixture.whenStable();

    expect(text('version-backend')).toBe('Backend 0.1.0');
    expect(text('version-api')).toBe('API 0.1.0');
  });

  it('shows "unknown" for fields the backend does not send', async () => {
    http.expectOne('/actuator/info').flush({});
    await fixture.whenStable();

    expect(text('version-backend')).toBe('Backend unknown');
    expect(text('version-api')).toBe('API unknown');
  });

  it('shows "backend unavailable" and keeps the frontend version when the backend cannot be reached', async () => {
    http
      .expectOne('/actuator/info')
      .flush('Bad gateway', { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();

    expect(text('version-backend-unavailable')).toBe('Backend unavailable');
    expect(text('version-frontend')).toBe(`Frontend ${FRONTEND_VERSION}`);
    expect(text('version-api')).toBeUndefined();
  });
});
