/** A made-up origin: only used to check where a path would lead. */
const BASE = 'https://app.invalid';

/**
 * The `returnUrl` query parameter, if it is a path of this application, returned as the path a
 * browser would resolve. Anything else (another origin, `//host`, `/\host`, `javascript:`, a path
 * that leads elsewhere once tabs or new lines are removed…) is ignored, so a crafted link cannot
 * send the user away after they register or log in.
 */
export function safeReturnUrl(value: string | null | undefined): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return null;
  }
  const resolved = new URL(value, BASE);
  if (resolved.origin !== BASE) {
    return null;
  }
  return resolved.pathname + resolved.search + resolved.hash;
}
