import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Placeholder landing page, replaced by the accounts and profile features (M1). */
@Component({
  selector: 'app-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section data-testid="home">
      <h1 i18n="@@home.title">BlankJava</h1>
      <p i18n="@@home.message">Foundation is up. Accounts arrive with milestone M1.</p>
    </section>
  `,
})
export class HomePage {}
