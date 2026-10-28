import { HttpClient } from '@angular/common/http';
import { DOCUMENT, Injectable, inject } from '@angular/core';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { sendClientLogs } from '../api/generated/fn/system/send-client-logs';
import { ClientLogEntry } from '../api/generated/models/client-log-entry';
import { TraceContext } from '../tracing/trace-context';

/** Contract limits of POST /api/client-logs (api/openapi.yaml). */
export const MAX_ENTRIES_PER_REQUEST = 20;
export const MAX_MESSAGE_LENGTH = 1000;
const MAX_URL_LENGTH = 500;

/** Shipping policy: batched, and throttled so an error loop cannot flood the backend. */
export const FLUSH_DELAY_MS = 5_000;
export const MAX_ENTRIES_PER_MINUTE = 20;

/**
 * Sends the browser's warnings and errors to the backend (`POST /api/client-logs`), where they are
 * written with `event.dataset=frontend` (docs/technical_analysis.md §6.3). Entries are batched:
 * sent when 20 are waiting or 5 s after the first one. Failures to send are ignored, never logged,
 * so shipping can never loop on itself.
 */
@Injectable({ providedIn: 'root' })
export class ClientLogShipper {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfiguration);
  private readonly trace = inject(TraceContext);
  private readonly document = inject(DOCUMENT);

  private queue: ClientLogEntry[] = [];
  private timer: ReturnType<typeof setTimeout> | null = null;
  private windowStart = 0;
  private acceptedInWindow = 0;

  enqueue(level: ClientLogEntry['level'], message: string): void {
    if (!this.withinRate()) {
      return;
    }
    this.queue.push({
      level,
      message: (message || '(empty message)').slice(0, MAX_MESSAGE_LENGTH),
      traceId: this.trace.lastTraceId() ?? undefined,
      url: this.document.location?.pathname?.slice(0, MAX_URL_LENGTH),
      timestamp: new Date().toISOString(),
    });
    if (this.queue.length >= MAX_ENTRIES_PER_REQUEST) {
      this.flush();
    } else if (this.timer === null) {
      this.timer = setTimeout(() => this.flush(), FLUSH_DELAY_MS);
    }
  }

  /** Sends what is waiting now (also used before the page unloads, later). */
  flush(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.queue.length === 0) {
      return;
    }
    const body = this.queue.splice(0, MAX_ENTRIES_PER_REQUEST);
    sendClientLogs(this.http, this.api.rootUrl, { body }).subscribe({ error: () => undefined });
  }

  private withinRate(): boolean {
    const now = Date.now();
    if (now - this.windowStart >= 60_000) {
      this.windowStart = now;
      this.acceptedInWindow = 0;
    }
    if (this.acceptedInWindow >= MAX_ENTRIES_PER_MINUTE) {
      return false;
    }
    this.acceptedInWindow++;
    return true;
  }
}
