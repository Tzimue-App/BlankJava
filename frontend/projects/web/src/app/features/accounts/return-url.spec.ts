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
    '/\t/evil.example',
    '/\n/evil.example',
    '/\r\n/evil.example',
  ])('ignores %j', (url) => {
    expect(safeReturnUrl(url)).toBeNull();
  });

  it('returns the path as the browser would resolve it', () => {
    expect(safeReturnUrl('/me/../settings?a=1#top')).toBe('/settings?a=1#top');
  });
});
