import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { VersionService } from '../../core/version/version.service';

/** Versions of the running system, to quote in bug reports. */
@Component({
  selector: 'app-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer class="footer" data-testid="footer">
      <span data-testid="version-release" i18n="@@footer.release"
        >Release {{ versions.release() }}</span
      >
      <span data-testid="version-frontend" i18n="@@footer.frontend"
        >Frontend {{ versions.frontend }}</span
      >
      @switch (state()) {
        @case ('loading') {
          <span data-testid="version-backend-loading" i18n="@@footer.backendLoading"
            >Backend …</span
          >
        }
        @case ('unavailable') {
          <span data-testid="version-backend-unavailable" i18n="@@footer.backendUnavailable"
            >Backend unavailable</span
          >
        }
        @default {
          <span data-testid="version-backend" i18n="@@footer.backend"
            >Backend {{ backend()?.backend }}</span
          >
          <span data-testid="version-api" i18n="@@footer.api">API {{ backend()?.api }}</span>
        }
      }
    </footer>
  `,
  styles: `
    .footer {
      display: flex;
      flex-wrap: wrap;
      gap: 0 1.5rem;
      padding: 0.75rem 1rem;
      font-size: 0.8rem;
      color: var(--mat-sys-on-surface-variant);
      border-top: 1px solid var(--mat-sys-outline-variant);
    }
  `,
})
export class Footer {
  protected readonly versions = inject(VersionService);

  /** `loading`, `unavailable` or `ready`, for the template switch. */
  protected readonly state = computed(() => {
    const backend = this.versions.backend();
    return typeof backend === 'string' ? backend : 'ready';
  });

  protected readonly backend = computed(() => {
    const backend = this.versions.backend();
    return typeof backend === 'string' ? null : backend;
  });
}
