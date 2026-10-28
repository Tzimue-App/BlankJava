import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { LoggerService } from '../logging/logger';
import { TraceContext } from '../tracing/trace-context';

const TRACED_PREFIXES = ['/api/', '/actuator/'];
const CLIENT_LOGS_PATH = '/api/client-logs';

/**
 * Adds a W3C `traceparent` to every call to our backend, so its Nginx and backend log lines share a
 * trace id, and reports calls that failed on the server side (5xx) or never reached it (status 0).
 * Calls to other origins get no header; failures of the log shipping itself are never reported.
 */
export const tracingInterceptor: HttpInterceptorFn = (request, next) => {
  if (!TRACED_PREFIXES.some((prefix) => request.url.startsWith(prefix))) {
    return next(request);
  }
  const { traceId, traceparent } = inject(TraceContext).start();
  const logger = inject(LoggerService);

  return next(request.clone({ setHeaders: { traceparent } })).pipe(
    tap({
      error: (error: unknown) => {
        const failedOnServer =
          error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
        if (failedOnServer && !request.url.startsWith(CLIENT_LOGS_PATH)) {
          const path = request.url.split('?')[0];
          logger.warn(
            `${request.method} ${path} failed with HTTP ${error.status} (trace ${traceId})`,
          );
        }
      },
    }),
  );
};
