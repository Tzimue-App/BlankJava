import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TraceContext } from '../tracing/trace-context';
import {
  ClientLogShipper,
  FLUSH_DELAY_MS,
  MAX_ENTRIES_PER_MINUTE,
  MAX_ENTRIES_PER_REQUEST,
  MAX_MESSAGE_LENGTH,
} from './client-log-shipper';

describe('ClientLogShipper', () => {
  let shipper: ClientLogShipper;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    shipper = TestBed.inject(ClientLogShipper);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('sends a batch 5 seconds after the first entry, with the last trace id and the page path', () => {
    const { traceId } = TestBed.inject(TraceContext).start();

    shipper.enqueue('error', 'First');
    shipper.enqueue('warn', 'Second');
    backend.expectNone('/api/client-logs');

    vi.advanceTimersByTime(FLUSH_DELAY_MS);

    const request = backend.expectOne('/api/client-logs');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual([
      expect.objectContaining({
        level: 'error',
        message: 'First',
        traceId,
        url: expect.any(String),
      }),
      expect.objectContaining({ level: 'warn', message: 'Second', traceId }),
    ]);
    expect(request.request.body[0].timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    request.flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('sends at once when 20 entries are waiting', () => {
    for (let i = 0; i < MAX_ENTRIES_PER_REQUEST; i++) {
      shipper.enqueue('warn', `Warning ${i}`);
    }

    const request = backend.expectOne('/api/client-logs');
    expect(request.request.body).toHaveLength(MAX_ENTRIES_PER_REQUEST);
    request.flush(null, { status: 202, statusText: 'Accepted' });
    vi.advanceTimersByTime(FLUSH_DELAY_MS);
    backend.expectNone('/api/client-logs');
  });

  it('accepts at most 20 entries per minute', () => {
    for (let i = 0; i < MAX_ENTRIES_PER_MINUTE + 5; i++) {
      shipper.enqueue('error', `Loop ${i}`);
    }
    backend.expectOne('/api/client-logs').flush(null);
    vi.advanceTimersByTime(FLUSH_DELAY_MS);
    backend.expectNone('/api/client-logs');

    vi.advanceTimersByTime(60_000);
    shipper.enqueue('error', 'Next minute');
    vi.advanceTimersByTime(FLUSH_DELAY_MS);
    expect(backend.expectOne('/api/client-logs').request.body).toHaveLength(1);
  });

  it('truncates messages to the contract limit and replaces an empty one', () => {
    shipper.enqueue('error', 'x'.repeat(MAX_MESSAGE_LENGTH + 50));
    shipper.enqueue('error', '');
    shipper.flush();

    const [long, empty] = backend.expectOne('/api/client-logs').request.body;
    expect(long.message).toHaveLength(MAX_MESSAGE_LENGTH);
    expect(empty.message).toBe('(empty message)');
  });

  it('ignores failures to send (nothing to log them with)', () => {
    shipper.enqueue('error', 'Lost');
    shipper.flush();

    expect(() =>
      backend
        .expectOne('/api/client-logs')
        .flush('Down', { status: 503, statusText: 'Service Unavailable' }),
    ).not.toThrow();
  });

  it('does nothing when flushed with an empty queue', () => {
    shipper.flush();

    backend.expectNone('/api/client-logs');
  });
});
