import { DOCUMENT, InjectionToken, inject } from '@angular/core';

/**
 * Browser globals, injected instead of read directly, so the code stays compatible with
 * server-side rendering (docs/technical_analysis.md §3.4). This file is the only place in the app
 * allowed to reach `window` or Web Storage; both are `null` outside a browser.
 */
export const WINDOW = new InjectionToken<Window | null>('WINDOW', {
  providedIn: 'root',
  factory: () => inject(DOCUMENT).defaultView,
});

export const STORAGE = new InjectionToken<Storage | null>('STORAGE', {
  providedIn: 'root',
  factory: () => {
    try {
      return inject(WINDOW)?.localStorage ?? null;
    } catch {
      // Storage access can throw (privacy settings, sandboxed iframes)
      return null;
    }
  },
});
