import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DEFAULT_RUNTIME_CONFIG, RuntimeConfigService } from './runtime-config';

describe('RuntimeConfigService', () => {
  let service: RuntimeConfigService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RuntimeConfigService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts with the defaults', () => {
    expect(service.config()).toEqual(DEFAULT_RUNTIME_CONFIG);
  });

  it('loads /config.json', async () => {
    const loading = service.load();
    http.expectOne('/config.json').flush({ release: '0.1.0-b' });
    await loading;

    expect(service.config().release).toBe('0.1.0-b');
  });

  it('keeps the defaults for missing keys', async () => {
    const loading = service.load();
    http.expectOne('/config.json').flush({});
    await loading;

    expect(service.config()).toEqual(DEFAULT_RUNTIME_CONFIG);
  });

  it('keeps the defaults when the file cannot be loaded', async () => {
    const loading = service.load();
    http.expectOne('/config.json').flush('Not found', { status: 404, statusText: 'Not Found' });
    await loading;

    expect(service.config()).toEqual(DEFAULT_RUNTIME_CONFIG);
  });
});
