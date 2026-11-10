import { FormControl, FormGroup } from '@angular/forms';
import {
  codePointLength,
  displayNameValidator,
  emailValidator,
  matchingValidator,
  passwordLength,
  passwordValidator,
} from './account-validators';

const check = (validator: typeof emailValidator, value: unknown) =>
  validator(new FormControl(value));

describe('account validators', () => {
  describe('emailValidator', () => {
    it.each([
      'jane@example.com',
      '  Jane.Doe+news@Example.COM  ',
      "o'brien!#$%&*/=?^_`{|}~-@mail.example.org",
      'élodie@exemple.fr',
      'jane@bücher.example',
      'jane@xn--bcher-kva.example',
    ])('accepts %s', (email) => {
      expect(check(emailValidator, email)).toBeNull();
    });

    it.each([
      'jane',
      '@example.com',
      'jane@',
      'jane@@example.com',
      'ja@ne@example.com',
      'jane@localhost',
      'jane@example..com',
      'jane@-example.com',
      'jane@example-.com',
      'jane@example.123',
      '.jane@example.com',
      'jane.@example.com',
      'ja..ne@example.com',
      'ja ne@example.com',
      '"jane"@example.com',
      'jane@exa_mple.com',
    ])('refuses %s as invalid_format', (email) => {
      expect(check(emailValidator, email)).toEqual({ invalid_format: true });
    });

    it('needs a value', () => {
      expect(check(emailValidator, '   ')).toEqual({ required: true });
      expect(check(emailValidator, null)).toEqual({ required: true });
    });

    it('limits the address to 254 characters, the local part to 64 and a label to 63', () => {
      const label = 'a'.repeat(63);
      const longest = `${'j'.repeat(64)}@${label}.${label}.${'b'.repeat(61)}`;
      expect(codePointLength(longest)).toBe(254);
      expect(check(emailValidator, longest)).toBeNull();
      expect(check(emailValidator, longest + 'b')).toEqual({ too_long: true });
      expect(check(emailValidator, `${'j'.repeat(65)}@example.com`)).toEqual({
        invalid_format: true,
      });
      expect(check(emailValidator, `jane@${'a'.repeat(64)}.com`)).toEqual({
        invalid_format: true,
      });
    });
  });

  describe('displayNameValidator', () => {
    it('accepts 2 to 50 characters once trimmed, counted in code points', () => {
      expect(check(displayNameValidator, ' Jo ')).toBeNull();
      expect(check(displayNameValidator, '😀😀')).toBeNull();
      expect(check(displayNameValidator, '😀'.repeat(50))).toBeNull();
    });

    it('reports the broken rule', () => {
      expect(check(displayNameValidator, '  ')).toEqual({ required: true });
      expect(check(displayNameValidator, ' J ')).toEqual({ too_short: true });
      expect(check(displayNameValidator, '😀')).toEqual({ too_short: true });
      expect(check(displayNameValidator, 'a'.repeat(51))).toEqual({ too_long: true });
      expect(check(displayNameValidator, 'Ja\u0007ne')).toEqual({ invalid_format: true });
      expect(check(displayNameValidator, 'Ja\u0085ne')).toEqual({ invalid_format: true });
    });
  });

  describe('passwordValidator', () => {
    it('accepts 15 to 64 code points, spaces included', () => {
      expect(check(passwordValidator, 'tangerine river')).toBeNull();
      expect(check(passwordValidator, '😀'.repeat(64))).toBeNull();
    });

    it('reports the broken rule', () => {
      expect(check(passwordValidator, '')).toEqual({ required: true });
      expect(check(passwordValidator, 'a'.repeat(14))).toEqual({ too_short: true });
      expect(check(passwordValidator, '😀'.repeat(14))).toEqual({ too_short: true });
      expect(check(passwordValidator, 'a'.repeat(65))).toEqual({ too_long: true });
    });

    it('counts after NFKC, as the backend does', () => {
      // "ﬃ" (one character) becomes "ffi" (three): 13 + 1 characters typed, 16 counted
      const typed = 'a'.repeat(13) + 'ﬃ';
      expect(typed.length).toBe(14);
      expect(passwordLength(typed)).toBe(16);
      expect(check(passwordValidator, typed)).toBeNull();

      // The reverse: 65 characters typed whose NFKC form has 64 ("e" + combining acute → "é")
      const composed = 'a'.repeat(63) + 'é';
      expect(composed.length).toBe(65);
      expect(passwordLength(composed)).toBe(64);
      expect(check(passwordValidator, composed)).toBeNull();

      // 64 characters typed that become 66: refused, like the backend
      const expanded = 'a'.repeat(63) + 'ﬃ';
      expect(check(passwordValidator, expanded)).toEqual({ too_long: true });
    });
  });

  describe('matchingValidator', () => {
    const form = () =>
      new FormGroup(
        { password: new FormControl('one'), confirmation: new FormControl('') },
        { validators: matchingValidator('password', 'confirmation') },
      );

    it('flags a different confirmation on the confirmation field, and clears it once equal', () => {
      const group = form();
      group.controls.confirmation.setValue('two');
      expect(group.controls.confirmation.errors).toEqual({ mismatch: true });

      group.controls.confirmation.setValue('one');
      expect(group.controls.confirmation.errors).toBeNull();

      group.controls.password.setValue('three');
      expect(group.controls.confirmation.errors).toEqual({ mismatch: true });
    });

    it('waits until something is typed, and keeps the other errors of the field', () => {
      const group = form();
      expect(group.controls.confirmation.errors).toBeNull();

      group.controls.confirmation.setErrors({ required: true });
      group.controls.password.setValue('changed');
      expect(group.controls.confirmation.errors).toEqual({ required: true });
    });

    it('ignores a group without the confirmation control', () => {
      const group = new FormGroup(
        { password: new FormControl('x') },
        { validators: matchingValidator('password', 'confirmation') },
      );
      expect(group.valid).toBe(true);
    });
  });
});
