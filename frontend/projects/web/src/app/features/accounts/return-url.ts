/**
 * The `returnUrl` query parameter, if it is a path of this application. Anything else (another
 * origin, `//host`, `/\host`, `javascript:`…) is ignored, so a crafted link cannot send the user
 * away after they register or log in.
 */
export function safeReturnUrl(value: string | null | undefined): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return null;
  }
  return value;
}
