import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Validators of the account forms, mirroring the backend rules (backend `README.md`, *Accounts and
 * sessions*) so the form refuses what the backend would refuse, and accepts what it would accept.
 * Lengths are counted in code points, like the backend (an emoji is one character, not two).
 *
 * Error keys are the contract's `FieldError` codes (`required`, `too_short`, `too_long`,
 * `invalid_format`), so a client-side error and the same error returned by the server show the
 * same message.
 */

export const EMAIL_MAX = 254;
export const DISPLAY_NAME_MIN = 2;
export const DISPLAY_NAME_MAX = 50;
export const PASSWORD_MIN = 15;
export const PASSWORD_MAX = 64;

const LOCAL_PART_MAX = 64;
const LABEL_MAX = 63;
const LOCAL_PART_SYMBOLS = "!#$%&'*+/=?^_`{|}~.-";
const LETTER_OR_DIGIT = /^[\p{L}\p{Nd}]$/u;
const DIGIT = /^\p{Nd}$/u;
/** Java's `Character.isISOControl`: the Unicode `Cc` category (C0 controls, DEL, C1 controls). */
const CONTROL = /\p{Cc}/u;

/** Number of Unicode code points, as the backend counts them. */
export function codePointLength(value: string): number {
  return [...value].length;
}

/** Password length as the backend measures it: in code points, after NFKC normalisation. */
export function passwordLength(value: string): number {
  return codePointLength(value.normalize('NFKC'));
}

/** `local@domain`, trimmed: dot-atom local part, at least two domain labels (backend rules). */
export const emailValidator: ValidatorFn = (control) => {
  const value = text(control).trim();
  if (value === '') {
    return { required: true };
  }
  if (codePointLength(value) > EMAIL_MAX) {
    return { too_long: true };
  }
  return isEmailAddress(value) ? null : { invalid_format: true };
};

/** 2 to 50 code points once trimmed, no control character. */
export const displayNameValidator: ValidatorFn = (control) => {
  const value = text(control).trim();
  const length = codePointLength(value);
  if (length === 0) {
    return { required: true };
  }
  if (length < DISPLAY_NAME_MIN) {
    return { too_short: true };
  }
  if (length > DISPLAY_NAME_MAX) {
    return { too_long: true };
  }
  return CONTROL.test(value) ? { invalid_format: true } : null;
};

/** 15 to 64 code points after NFKC; not trimmed (spaces are part of a password). */
export const passwordValidator: ValidatorFn = (control) => {
  const value = text(control);
  if (value === '') {
    return { required: true };
  }
  const length = passwordLength(value);
  if (length < PASSWORD_MIN) {
    return { too_short: true };
  }
  return length > PASSWORD_MAX ? { too_long: true } : null;
};

/**
 * Group validator: the control `confirmation` must equal `original`. The error `mismatch` is set on
 * the confirmation control itself, so it shows under that field.
 */
export function matchingValidator(original: string, confirmation: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const confirm = group.get(confirmation);
    if (!confirm) {
      return null;
    }
    const mismatch = confirm.value !== '' && confirm.value !== group.get(original)?.value;
    const errors: ValidationErrors = { ...confirm.errors };
    delete errors['mismatch'];
    if (mismatch) {
      errors['mismatch'] = true;
    }
    confirm.setErrors(Object.keys(errors).length > 0 ? errors : null, { emitEvent: false });
    return null;
  };
}

function text(control: AbstractControl): string {
  return typeof control.value === 'string' ? control.value : '';
}

function isEmailAddress(address: string): boolean {
  const at = address.indexOf('@');
  if (at <= 0 || at !== address.lastIndexOf('@') || at === address.length - 1) {
    return false;
  }
  return isLocalPart(address.slice(0, at)) && isDomain(address.slice(at + 1));
}

function isLocalPart(local: string): boolean {
  if (
    codePointLength(local) > LOCAL_PART_MAX ||
    local.startsWith('.') ||
    local.endsWith('.') ||
    local.includes('..')
  ) {
    return false;
  }
  return [...local].every((c) => LETTER_OR_DIGIT.test(c) || LOCAL_PART_SYMBOLS.includes(c));
}

function isDomain(domain: string): boolean {
  const labels = domain.split('.');
  if (labels.length < 2) {
    return false;
  }
  const validLabels = labels.every(
    (label) =>
      label !== '' &&
      codePointLength(label) <= LABEL_MAX &&
      !label.startsWith('-') &&
      !label.endsWith('-') &&
      [...label].every((c) => LETTER_OR_DIGIT.test(c) || c === '-'),
  );
  return validLabels && ![...labels[labels.length - 1]].every((c) => DIGIT.test(c));
}
