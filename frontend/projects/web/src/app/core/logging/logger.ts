import { Injectable, InjectionToken, inject, isDevMode } from '@angular/core';
import { ClientLogShipper } from './client-log-shipper';

/** Whether `debug` messages are written: development builds only. */
export const LOG_DEBUG_ENABLED = new InjectionToken<boolean>('LOG_DEBUG_ENABLED', {
  providedIn: 'root',
  factory: () => isDevMode(),
});

/**
 * Application logger (docs/technical_analysis.md §6.3). Writes to the browser console; warnings and
 * errors are also sent to the backend logs. Never pass personal data (e-mails, tokens): the
 * backend masks e-mails, but nothing else.
 */
@Injectable({ providedIn: 'root' })
export class LoggerService {
  private readonly shipper = inject(ClientLogShipper);
  private readonly debugEnabled = inject(LOG_DEBUG_ENABLED);

  debug(message: string): void {
    if (this.debugEnabled) {
      console.debug(message);
    }
  }

  info(message: string): void {
    console.info(message);
  }

  warn(message: string): void {
    console.warn(message);
    this.shipper.enqueue('warn', message);
  }

  error(message: string, error?: unknown): void {
    console.error(message, ...(error === undefined ? [] : [error]));
    this.shipper.enqueue('error', error === undefined ? message : `${message}: ${describe(error)}`);
  }
}

function describe(error: unknown): string {
  if (error instanceof Error) {
    return `${error.name}: ${error.message}`;
  }
  return typeof error === 'string' ? error : (JSON.stringify(error) ?? String(error));
}
