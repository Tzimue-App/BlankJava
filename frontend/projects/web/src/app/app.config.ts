import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  ErrorHandler,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { RuntimeConfigService } from './core/config/runtime-config';
import { tracingInterceptor } from './core/http/tracing.interceptor';
import { GlobalErrorHandler } from './core/logging/global-error-handler';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Uncaught errors go to the backend logs (docs/technical_analysis.md §6.3)
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    // traceparent on every API call: Nginx and backend log lines share its trace id
    provideHttpClient(withFetch(), withInterceptors([tracingInterceptor])),
    provideRouter(routes, withComponentInputBinding()),
    // /config.json is read before the first render (runtime configuration, §3.4)
    provideAppInitializer(() => inject(RuntimeConfigService).load()),
  ],
};
