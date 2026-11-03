import { TestBed } from '@angular/core/testing';
import { TraceContext } from './trace-context';

describe('TraceContext', () => {
  it('creates W3C traceparent values with a fresh trace id each time', () => {
    const trace = TestBed.inject(TraceContext);

    const first = trace.start();
    const second = trace.start();

    expect(first.traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
    expect(first.traceparent).toContain(first.traceId);
    expect(second.traceId).not.toBe(first.traceId);
  });

  it('remembers the last trace id', () => {
    const trace = TestBed.inject(TraceContext);
    expect(trace.lastTraceId()).toBeNull();

    const { traceId } = trace.start();

    expect(trace.lastTraceId()).toBe(traceId);
  });
});
