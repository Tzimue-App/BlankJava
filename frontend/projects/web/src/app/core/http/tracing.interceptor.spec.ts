import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LoggerService } from '../logging/logger';
import { TraceContext } from '../tracing/trace-context';
import { tracingInterceptor } from './tracing.interceptor';

describe('tracingInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const warn = vi.fn();

  beforeEach(() => {
    warn.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([tracingInterceptor])),
        provideHttpClientTesting(),
        { provide: LoggerService, useValue: { warn } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds a traceparent to API and actuator calls, and remembers its trace id', () => {
    http.get('/api/users/me').subscribe();
    http.get('/actuator/info').subscribe();

    const api = backend.expectOne('/api/users/me');
    const actuator = backend.expectOne('/actuator/info');
    const traceparent = api.request.headers.get('traceparent');
    expect(traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
    expect(actuator.request.headers.has('traceparent')).toBe(true);
    api.flush({});
    actuator.flush({});

    expect(TestBed.inject(TraceContext).lastTraceId()).not.toBeNull();
  });

  it('does not touch calls to other paths or origins', () => {
    http.get('/config.json').subscribe();
    http.get('https://example.org/api/x').subscribe();

    expect(backend.expectOne('/config.json').request.headers.has('traceparent')).toBe(false);
    expect(backend.expectOne('https://example.org/api/x').request.headers.has('traceparent')).toBe(
      false,
    );
  });

  it('reports server failures and unreachable backends with the trace id, without query string', () => {
    http.get('/api/users/me?verbose=true').subscribe({ error: () => undefined });
    const request = backend.expectOne('/api/users/me?verbose=true');
    const traceId = request.request.headers.get('traceparent')!.split('-')[1];
    request.flush('Boom', { status: 502, statusText: 'Bad Gateway' });

    http.get('/api/other').subscribe({ error: () => undefined });
    backend.expectOne('/api/other').error(new ProgressEvent('error'));

    expect(warn).toHaveBeenCalledWith(`GET /api/users/me failed with HTTP 502 (trace ${traceId})`);
    expect(warn).toHaveBeenCalledTimes(2);
  });

  it('does not report client errors or failures of the log shipping itself', () => {
    http.get('/api/users/me').subscribe({ error: () => undefined });
    backend.expectOne('/api/users/me').flush('Nope', { status: 401, statusText: 'Unauthorized' });
    http.post('/api/client-logs', []).subscribe({ error: () => undefined });
    backend
      .expectOne('/api/client-logs')
      .flush('Down', { status: 503, statusText: 'Service Unavailable' });

    expect(warn).not.toHaveBeenCalled();
  });
});
