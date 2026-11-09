import { DOCUMENT, InjectionToken, inject } from '@angular/core';

/**
 * Browser globals, injected instead of read directly, so the code stays compatible with
 * server-side rendering (docs/technical_analysis.md §3.4). This file is the only place in the app
 * allowed to reach `window`, Web Storage or Web Locks; all are `null` outside a browser.
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

/**
 * The Web Locks API, shared by the tabs of one browser; `null` where the browser lacks it (or
 * outside a browser). Used to serialise session refreshes across tabs (`AuthSession`).
 */
export const LOCKS = new InjectionToken<LockManager | null>('LOCKS', {
  providedIn: 'root',
  factory: () => inject(WINDOW)?.navigator?.locks ?? null,
});
