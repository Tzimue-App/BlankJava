import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { User } from '../../core/api/generated/models/user';
import { TraceContext } from '../../core/tracing/trace-context';
import { ProfileService } from './profile.service';

type State =
  | { kind: 'loading' }
  | { kind: 'loaded'; user: User; memberSince: string }
  | { kind: 'failed'; traceId: string | null };

/** Registration date in the browser's language, e.g. "November 5, 2026" or "5 novembre 2026". */
const MEMBER_SINCE = new Intl.DateTimeFormat(undefined, { dateStyle: 'long' });

/**
 * `/me`: the logged-in user's profile, read from `GET /api/users/me` on every visit. Every value is
 * rendered as text by interpolation, never as HTML.
 */
@Component({
  selector: 'app-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  template: `
    @switch (state().kind) {
      @case ('loading') {
        <p role="status" data-testid="profile-loading" i18n="@@profile.loading">
          Loading your profile…
        </p>
      }
      @case ('failed') {
        <div class="banner" role="alert" data-testid="profile-error-banner">
          <span i18n="@@profile.failure">Your profile could not be loaded.</span>
          @if (traceId(); as traceId) {
            <span i18n="@@profile.failureTrace">
              Reference: <span class="trace" data-testid="profile-trace-id">{{ traceId }}</span>
            </span>
          }
          <button mat-button type="button" (click)="load()" data-testid="profile-retry">
            <ng-container i18n="@@profile.retry">Try again</ng-container>
          </button>
        </div>
      }
      @case ('loaded') {
        @if (loaded(); as profile) {
          <section aria-labelledby="profile-name">
            <h1 id="profile-name" data-testid="profile-display-name">
              {{ profile.user.displayName }}
            </h1>
            <dl>
              <dt i18n="@@profile.email">E-mail address</dt>
              <dd data-testid="profile-email">{{ profile.user.email }}</dd>
              <dt i18n="@@profile.memberSince">Member since</dt>
              <dd>
                <time [attr.datetime]="profile.user.createdAt" data-testid="profile-member-since">
                  {{ profile.memberSince }}
                </time>
              </dd>
            </dl>
          </section>
        }
      }
    }
  `,
  styles: `
    :host {
      display: block;
      max-width: 40rem;
      margin: 0 auto;
    }
    h1 {
      overflow-wrap: anywhere;
    }
    dt {
      font-weight: 500;
      margin-top: 1rem;
    }
    dd {
      margin: 0.25rem 0 0;
      overflow-wrap: anywhere;
    }
    .banner {
      padding: 0.75rem 1rem;
      border-radius: 0.5rem;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .trace {
      font-family: monospace;
    }
  `,
})
export class ProfilePage {
  private readonly profiles = inject(ProfileService);
  private readonly traces = inject(TraceContext);

  protected readonly state = signal<State>({ kind: 'loading' });

  constructor() {
    void this.load();
  }

  protected loaded(): Extract<State, { kind: 'loaded' }> | null {
    const state = this.state();
    return state.kind === 'loaded' ? state : null;
  }

  protected traceId(): string | null {
    const state = this.state();
    return state.kind === 'failed' ? state.traceId : null;
  }

  async load(): Promise<void> {
    this.state.set({ kind: 'loading' });
    try {
      const user = await this.profiles.currentUser();
      this.state.set({
        kind: 'loaded',
        user,
        memberSince: MEMBER_SINCE.format(new Date(user.createdAt)),
      });
    } catch (error) {
      // A 401 never gets here for long: the interceptor refreshes or sends the user to /login
      const body = error instanceof HttpErrorResponse ? error.error : null;
      const traceId = typeof body?.traceId === 'string' ? body.traceId : this.traces.lastTraceId();
      this.state.set({ kind: 'failed', traceId });
    }
  }
}
