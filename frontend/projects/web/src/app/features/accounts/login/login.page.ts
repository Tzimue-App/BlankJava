import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../../core/api/generated/api';
import { login } from '../../../core/api/generated/fn/auth/login';
import { AuthSession } from '../../../core/auth/auth-session';
import { TraceContext } from '../../../core/tracing/trace-context';
import { safeReturnUrl } from '../return-url';

/**
 * `/login`: opens a session. The password is not checked against the policy here: whatever is
 * typed goes to the server, and any mismatch gets the same message, so the page never tells which
 * of the e-mail or the password was wrong.
 */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './login.page.html',
  styleUrl: '../account-form.scss',
})
export class LoginPage {
  private readonly api = inject(Api);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);
  private readonly traces = inject(TraceContext);

  /** Where to go once logged in (query parameter, bound by the router). */
  readonly returnUrl = input<string | undefined>();

  readonly form = inject(NonNullableFormBuilder).group({
    email: ['', Validators.required],
    password: ['', Validators.required],
  });

  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);
  /** The credentials did not match an account. */
  readonly rejected = signal(false);
  /** Trace id of an unexpected failure, shown in the error banner. */
  readonly failure = signal<{ traceId: string | null } | null>(null);

  private readonly status = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  readonly canSubmit = computed(() => this.status() === 'VALID' && !this.submitting());

  togglePassword(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  async submit(): Promise<void> {
    if (!this.canSubmit()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.rejected.set(false);
    this.failure.set(null);
    const { email, password } = this.form.getRawValue();
    try {
      this.session.start(await this.api.invoke(login, { body: { email, password } }));
      await this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/me');
    } catch (error) {
      this.showFailure(error);
    } finally {
      this.submitting.set(false);
    }
  }

  private showFailure(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.rejected.set(true);
      this.form.controls.password.reset();
      return;
    }
    const body = error instanceof HttpErrorResponse ? error.error : null;
    const traceId = typeof body?.traceId === 'string' ? body.traceId : this.traces.lastTraceId();
    this.failure.set({ traceId });
  }
}
