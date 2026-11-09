import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink } from '@angular/router';
import { AuthSession } from '../../core/auth/auth-session';

/** Application header: brand link, and *Log out* while a user is logged in. */
@Component({
  selector: 'app-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatToolbarModule, RouterLink],
  template: `
    <mat-toolbar class="header">
      <a routerLink="/" class="brand" i18n="@@shell.brand">BlankJava</a>
      <span class="spacer"></span>
      @if (session.isAuthenticated()) {
        <button
          mat-button
          type="button"
          (click)="logout()"
          [disabled]="leaving()"
          data-testid="header-logout"
          i18n="@@header.logout"
        >
          Log out
        </button>
      }
    </mat-toolbar>
  `,
  styles: `
    .brand {
      color: inherit;
      text-decoration: none;
    }
    .spacer {
      flex: 1;
    }
  `,
})
export class Header {
  protected readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  protected readonly leaving = signal(false);

  /** Ends the session (also when the backend cannot be reached) and goes to the login page. */
  async logout(): Promise<void> {
    this.leaving.set(true);
    try {
      await this.session.logout();
      await this.router.navigateByUrl('/login');
    } finally {
      this.leaving.set(false);
    }
  }
}
