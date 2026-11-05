import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Settings read at startup from `/config.json`, so one image runs in every environment. */
export interface RuntimeConfig {
  /** Project release of the deployed image, e.g. `0.1.0-b` (written from `APP_RELEASE`). */
  release: string;
}

export const DEFAULT_RUNTIME_CONFIG: RuntimeConfig = { release: 'unknown' };

@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {
  private readonly http = inject(HttpClient);
  private readonly state = signal<RuntimeConfig>(DEFAULT_RUNTIME_CONFIG);

  readonly config = this.state.asReadonly();

  /** Called once by the app initializer. A missing or broken file keeps the defaults. */
  async load(): Promise<void> {
    try {
      const loaded = await firstValueFrom(this.http.get<Partial<RuntimeConfig>>('/config.json'));
      this.state.set({ ...DEFAULT_RUNTIME_CONFIG, ...loaded });
    } catch {
      this.state.set(DEFAULT_RUNTIME_CONFIG);
    }
  }
}
