import { Injectable, signal } from '@angular/core';

/** A W3C Trace Context for one outgoing request. */
export interface TraceStart {
  traceId: string;
  /** Value of the `traceparent` header: `00-<trace id>-<span id>-01`. */
  traceparent: string;
}

/**
 * Creates the trace id sent with every API call (`traceparent`), so the Nginx and backend log lines
 * of that call share it (docs/technical_analysis.md §6.3). The last one is kept: it is attached to
 * the browser's log events and can be shown to the user as an error reference.
 */
@Injectable({ providedIn: 'root' })
export class TraceContext {
  private readonly last = signal<string | null>(null);

  readonly lastTraceId = this.last.asReadonly();

  start(): TraceStart {
    const traceId = randomHex(16);
    this.last.set(traceId);
    return { traceId, traceparent: `00-${traceId}-${randomHex(8)}-01` };
  }
}

function randomHex(bytes: number): string {
  const values = crypto.getRandomValues(new Uint8Array(bytes));
  return Array.from(values, (value) => value.toString(16).padStart(2, '0')).join('');
}
