import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../../core/api/generated/api';
import { register } from '../../../core/api/generated/fn/auth/register';
import { FieldError } from '../../../core/api/generated/models/field-error';
import { AuthSession } from '../../../core/auth/auth-session';
import { TraceContext } from '../../../core/tracing/trace-context';
import {
  DISPLAY_NAME_MAX,
  DISPLAY_NAME_MIN,
  PASSWORD_MAX,
  PASSWORD_MIN,
  displayNameValidator,
  emailValidator,
  matchingValidator,
  passwordLength,
  passwordValidator,
} from '../../../shared/validators/account-validators';
import { safeReturnUrl } from '../return-url';

type Field = 'email' | 'displayName' | 'password' | 'confirmation';

/** Error shown under a field, by field and error key (contract `FieldError` codes, `email_taken`…). */
const MESSAGES: Record<Field, Record<string, string>> = {
  email: {
    required: $localize`:@@register.email.required:Enter your e-mail address.`,
    too_long: $localize`:@@register.email.tooLong:The e-mail address is too long.`,
    invalid_format: $localize`:@@register.email.invalid:Enter a valid e-mail address, like name@example.com.`,
    email_taken: $localize`:@@register.email.taken:This e-mail address is already registered. Log in instead.`,
  },
  displayName: {
    required: $localize`:@@register.displayName.required:Enter a display name.`,
    too_short: $localize`:@@register.displayName.tooShort:The display name needs at least ${DISPLAY_NAME_MIN}:min: characters.`,
    too_long: $localize`:@@register.displayName.tooLong:The display name can have at most ${DISPLAY_NAME_MAX}:max: characters.`,
    invalid_format: $localize`:@@register.displayName.invalid:The display name contains a character that is not allowed.`,
  },
  password: {
    required: $localize`:@@register.password.required:Choose a password.`,
    too_short: $localize`:@@register.password.tooShort:The password needs at least ${PASSWORD_MIN}:min: characters.`,
    too_long: $localize`:@@register.password.tooLong:The password can have at most ${PASSWORD_MAX}:max: characters.`,
    invalid_format: $localize`:@@register.password.invalid:This password is not accepted.`,
    compromised_password: $localize`:@@register.password.compromised:This password appears in known data breaches. Choose another one.`,
  },
  confirmation: {
    required: $localize`:@@register.confirmation.required:Repeat the password.`,
    mismatch: $localize`:@@register.confirmation.mismatch:The passwords do not match.`,
  },
};

/**
 * `/register`: creates an account and opens its session. The form checks the same rules as the
 * backend; what only the server knows (a registered e-mail, a breached password) comes back as
 * field errors and is shown under the field it concerns.
 */
@Component({
  selector: 'app-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './register.page.html',
  styleUrl: '../account-form.scss',
})
export class RegisterPage {
  private readonly api = inject(Api);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);
  private readonly traces = inject(TraceContext);

  /** Where to go once registered (query parameter, bound by the router). */
  readonly returnUrl = input<string | undefined>();

  readonly form = inject(NonNullableFormBuilder).group(
    {
      email: ['', emailValidator],
      displayName: ['', displayNameValidator],
      password: ['', passwordValidator],
      confirmation: [''],
    },
    { validators: matchingValidator('password', 'confirmation') },
  );

  readonly passwordMax = PASSWORD_MAX;
  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);
  /** Trace id of an unexpected failure, shown in the error banner. */
  readonly failure = signal<{ traceId: string | null } | null>(null);

  private readonly status = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  private readonly password = toSignal(this.form.controls.password.valueChanges, {
    initialValue: '',
  });

  /** Password length the way the backend counts it (code points after NFKC). */
  readonly passwordLength = computed(() => passwordLength(this.password()));
  readonly canSubmit = computed(() => this.status() === 'VALID' && !this.submitting());

  /** The message of the first error of a field, once the user has touched it. */
  error(field: Field): string | null {
    const control = this.form.controls[field];
    if (!control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    const key = Object.keys(control.errors)[0];
    return MESSAGES[field][key] ?? MESSAGES[field]['required'];
  }

  togglePassword(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  async submit(): Promise<void> {
    if (!this.canSubmit()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.failure.set(null);
    const { email, displayName, password } = this.form.getRawValue();
    try {
      const session = await this.api.invoke(register, { body: { email, displayName, password } });
      this.session.start(session);
      await this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/me');
    } catch (error) {
      this.showFailure(error);
    } finally {
      this.submitting.set(false);
    }
  }

  private showFailure(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.setServerError('email', 'email_taken');
      return;
    }
    const fieldErrors = fieldErrorsOf(error);
    if (fieldErrors.length > 0 && fieldErrors.every((e) => e.field in this.form.controls)) {
      fieldErrors.forEach((e) => this.setServerError(e.field as Field, e.code));
      return;
    }
    const body = error instanceof HttpErrorResponse ? error.error : null;
    const traceId = typeof body?.traceId === 'string' ? body.traceId : this.traces.lastTraceId();
    this.failure.set({ traceId });
  }

  private setServerError(field: Field, code: string): void {
    const control = this.form.controls[field];
    control.setErrors({ ...control.errors, [code]: true });
    control.markAsTouched();
  }
}

function fieldErrorsOf(error: unknown): FieldError[] {
  if (!(error instanceof HttpErrorResponse) || error.status !== 400) {
    return [];
  }
  const errors: unknown = error.error?.errors;
  return Array.isArray(errors) ? (errors as FieldError[]) : [];
}
