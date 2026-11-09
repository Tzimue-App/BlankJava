import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it.each(['/me', '/me?tab=profile', '/a/b#c'])('keeps the application path %s', (url) => {
    expect(safeReturnUrl(url)).toBe(url);
  });

  it.each([
    undefined,
    null,
    '',
    'me',
    'https://evil.example/me',
    '//evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
  ])('ignores %s', (url) => {
    expect(safeReturnUrl(url)).toBeNull();
  });
});
