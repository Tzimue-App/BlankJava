import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Footer } from '../footer/footer';

/** Application frame: header, routed page, footer. Every route renders inside it. */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatToolbarModule, RouterLink, RouterOutlet, Footer],
  template: `
    <mat-toolbar class="header">
      <a routerLink="/" class="brand" i18n="@@shell.brand">BlankJava</a>
    </mat-toolbar>
    <main class="content">
      <router-outlet />
    </main>
    <app-footer />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }
    .brand {
      color: inherit;
      text-decoration: none;
    }
    .content {
      flex: 1;
      padding: 1.5rem 1rem;
    }
  `,
})
export class Shell {}
