import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { appConfig } from './app.config';
import { AuthSession } from './core/auth/auth-session';
import { RuntimeConfigService } from './core/config/runtime-config';

describe('appConfig', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
  });

  it('loads /config.json before the application starts', async () => {
    const http = TestBed.inject(HttpTestingController);
    const initStatus = TestBed.inject(ApplicationInitStatus);

    http.expectOne('/config.json').flush({ release: '0.1.0-b' });
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    await initStatus.donePromise;

    expect(initStatus.done).toBe(true);
    expect(TestBed.inject(RuntimeConfigService).config().release).toBe('0.1.0-b');
    http.verify();
  });

  it('restores the session from the refresh cookie before the application starts', async () => {
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/config.json').flush({});
    http.expectOne('/api/auth/refresh').flush({
      accessToken: 'token',
      expiresIn: 600,
      user: { id: 'id', email: 'jane@example.com', displayName: 'Jane', createdAt: 'x' },
    });
    await TestBed.inject(ApplicationInitStatus).donePromise;

    expect(TestBed.inject(AuthSession).currentUser()?.displayName).toBe('Jane');
  });

  it('bootstraps the root component around the router outlet', async () => {
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/config.json').flush({});
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    await TestBed.inject(ApplicationInitStatus).donePromise;

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
});
