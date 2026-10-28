import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { RuntimeConfigService } from '../config/runtime-config';
import { FRONTEND_VERSION } from './frontend-version.generated';

/** Subset of the backend's `/actuator/info` used by the footer. */
interface BackendInfo {
  build?: { version?: string };
  api?: string;
}

export interface BackendVersions {
  backend: string;
  api: string;
}

/** `loading` until `/actuator/info` answers, `unavailable` if it cannot be reached. */
export type BackendVersionState = 'loading' | 'unavailable' | BackendVersions;

/**
 * The four versions shown in the footer and quoted in bug reports
 * (docs/technical_analysis.md §8.2): project release, frontend, backend and API contract.
 */
@Injectable({ providedIn: 'root' })
export class VersionService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);
  private readonly backendState = signal<BackendVersionState>('loading');

  readonly frontend = FRONTEND_VERSION;
  readonly release = computed(() => this.runtimeConfig.config().release);
  readonly backend = this.backendState.asReadonly();

  constructor() {
    this.http.get<BackendInfo>('/actuator/info').subscribe({
      next: (info) =>
        this.backendState.set({
          backend: info.build?.version ?? 'unknown',
          api: info.api ?? 'unknown',
        }),
      error: () => this.backendState.set('unavailable'),
    });
  }
}
