import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps in-app paths, including query strings', () => {
    expect(safeReturnUrl('/orders/42?tab=history')).toBe('/orders/42?tab=history');
  });

  it('falls back to the dashboard when there is nothing usable', () => {
    expect(safeReturnUrl(undefined)).toBe('/dashboard');
    expect(safeReturnUrl('')).toBe('/dashboard');
  });

  it('refuses absolute and protocol-relative URLs', () => {
    expect(safeReturnUrl('https://evil.example/phish')).toBe('/dashboard');
    expect(safeReturnUrl('//evil.example')).toBe('/dashboard');
    expect(safeReturnUrl('/\\evil.example')).toBe('/dashboard');
  });

  it('refuses the public pages to avoid redirect loops', () => {
    expect(safeReturnUrl('/login')).toBe('/dashboard');
    expect(safeReturnUrl('/signup?x=1')).toBe('/dashboard');
  });
});
