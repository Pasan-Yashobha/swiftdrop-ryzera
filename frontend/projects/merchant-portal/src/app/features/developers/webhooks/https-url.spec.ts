import { FormControl } from '@angular/forms';
import { httpsUrl } from './https-url';

describe('httpsUrl', () => {
  const check = (value: unknown) => httpsUrl(new FormControl(value));

  it('accepts an https address', () => {
    expect(check('https://shop.example.com/hooks/swiftdrop?token=1')).toBeNull();
    expect(check('https://shop.example.com:8443/hook')).toBeNull();
  });

  it('leaves an empty value to the required validator', () => {
    expect(check('')).toBeNull();
    expect(check('   ')).toBeNull();
    expect(check(null)).toBeNull();
  });

  it('rejects other schemes, relative addresses and text', () => {
    expect(check('http://shop.example.com')).toEqual({ httpsUrl: true });
    expect(check('ftp://shop.example.com')).toEqual({ httpsUrl: true });
    expect(check('javascript:alert(1)')).toEqual({ httpsUrl: true });
    expect(check('/hooks/swiftdrop')).toEqual({ httpsUrl: true });
    expect(check('shop.example.com')).toEqual({ httpsUrl: true });
  });

  it('rejects embedded credentials', () => {
    expect(check('https://user@shop.example.com')).toEqual({ httpsUrl: true });
    expect(check('https://user:pass@shop.example.com')).toEqual({ httpsUrl: true });
  });
});
