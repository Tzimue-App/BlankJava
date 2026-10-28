import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { appConfig } from './app.config';
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
    await initStatus.donePromise;

    expect(initStatus.done).toBe(true);
    expect(TestBed.inject(RuntimeConfigService).config().release).toBe('0.1.0-b');
    http.verify();
  });

  it('bootstraps the root component around the router outlet', async () => {
    TestBed.inject(HttpTestingController).expectOne('/config.json').flush({});
    await TestBed.inject(ApplicationInitStatus).donePromise;

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
});
