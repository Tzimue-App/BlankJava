import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../api/generated/api';
import { logout as logoutSession } from '../api/generated/fn/auth/logout';
import { refresh as refreshSession } from '../api/generated/fn/auth/refresh';
import { SessionResponse } from '../api/generated/models/session-response';
import { User } from '../api/generated/models/user';
import { LOCKS } from '../platform/tokens';

/** Name of the Web Lock that serialises session refreshes across the tabs of one browser. */
export const REFRESH_LOCK = 'blankjava-auth-refresh';

/**
 * The client side of the session (docs/technical_analysis.md §5.2). The access token lives only in
 * a private field of this service: never in Web Storage or a cookie, so an injected script cannot
 * read it back after a reload. The refresh token is the backend's `HttpOnly` cookie; this service
 * never sees it.
 *
 * Every refresh of the app goes through {@link refresh}. The backend rotates the refresh cookie on
 * each use and refuses a cookie presented twice (#30); the tabs of a browser share that cookie, so
 * refreshes run one at a time across tabs, inside the Web Lock {@link REFRESH_LOCK}. Within a tab,
 * concurrent callers share one refresh.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly api = inject(Api);
  private readonly locks = inject(LOCKS);

  private readonly user = signal<User | null>(null);
  #accessToken: string | null = null;
  #refreshing: Promise<boolean> | null = null;

  /** The logged-in user, `null` when anonymous. */
  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);

  /** The access token for `Authorization: Bearer`, `null` when anonymous. */
  accessToken(): string | null {
    return this.#accessToken;
  }

  /** Opens the session returned by register, login or refresh. */
  start(session: SessionResponse): void {
    this.#accessToken = session.accessToken;
    this.user.set(session.user);
  }

  /** Forgets the session in this tab (the refresh cookie is ended by the logout call). */
  end(): void {
    this.#accessToken = null;
    this.user.set(null);
  }

  /**
   * Renews the session with the refresh cookie. Resolves `true` when renewed; `false` when there is
   * no valid cookie or the call failed, and the session is then ended. Concurrent calls in a tab
   * share one request; across tabs, refreshes wait for each other (Web Locks, where available).
   */
  refresh(): Promise<boolean> {
    this.#refreshing ??= this.serialised(() => this.callRefresh()).finally(() => {
      this.#refreshing = null;
    });
    return this.#refreshing;
  }

  /**
   * Logs out: revokes the refresh cookie on the backend, then forgets the session. The session is
   * forgotten even when the call fails (backend unreachable): the user asked to leave. Runs inside
   * the refresh lock, so it never presents a cookie that another tab is rotating at that moment.
   */
  async logout(): Promise<void> {
    try {
      await this.serialised(async () => {
        await this.api.invoke(logoutSession);
        return true;
      });
    } catch {
      // Nothing to do: the cookie expires on its own, and this tab forgets the session below
    } finally {
      this.end();
    }
  }

  /** Startup: restores the session of a previous page load, if its cookie is still valid. */
  async restore(): Promise<void> {
    await this.refresh();
  }

  private serialised(task: () => Promise<boolean>): Promise<boolean> {
    // Without Web Locks (old browsers), only the in-tab single flight applies
    return this.locks ? this.locks.request(REFRESH_LOCK, task) : task();
  }

  private async callRefresh(): Promise<boolean> {
    try {
      // Inside the lock: the cookie sent now is the one the previous holder left
      this.start(await this.api.invoke(refreshSession));
      return true;
    } catch {
      // 401 (no, expired or reused cookie) or unreachable backend: anonymous, not an error
      this.end();
      return false;
    }
  }
}
