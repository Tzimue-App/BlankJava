import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section data-testid="not-found">
      <h1 i18n="@@notFound.title">Page not found</h1>
      <p i18n="@@notFound.message">The page you are looking for does not exist.</p>
      <a routerLink="/" i18n="@@notFound.home">Back to the home page</a>
    </section>
  `,
})
export class NotFound {}
