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
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthSession } from './core/auth/auth-session';
import { RuntimeConfigService } from './core/config/runtime-config';
import { tracingInterceptor } from './core/http/tracing.interceptor';
import { GlobalErrorHandler } from './core/logging/global-error-handler';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Uncaught errors go to the backend logs (docs/technical_analysis.md §6.3)
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    // traceparent on every API call: Nginx and backend log lines share its trace id. The access
    // token is added inside it, so a retry after a refresh gets its own trace.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, tracingInterceptor])),
    provideRouter(routes, withComponentInputBinding()),
    // /config.json is read before the first render (runtime configuration, §3.4)
    provideAppInitializer(() => inject(RuntimeConfigService).load()),
    // The session of a previous page load is restored from the refresh cookie (anonymous if none)
    provideAppInitializer(() => inject(AuthSession).restore()),
  ],
};
