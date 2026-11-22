import { FormControl, FormGroup } from '@angular/forms';
import { safeReturnUrl } from '../../core/auth/return-url';
import { toHttpParams } from '../../core/api/query-params';
import { dateRangeValidator, endOfDayUtc, startOfDayUtc } from './date-range';
import { prettyJson } from './pretty-json';
import { templatePlaceholders } from './template-placeholders';

describe('prettyJson', () => {
  it('indents valid JSON', () => {
    expect(prettyJson('{"a":1,"b":[true]}')).toBe('{\n  "a": 1,\n  "b": [\n    true\n  ]\n}');
  });

  it('returns invalid JSON unchanged, and empty text for nothing', () => {
    expect(prettyJson('<html>oops')).toBe('<html>oops');
    expect(prettyJson(null)).toBe('');
  });
});

describe('date range helpers', () => {
  it('turns date input values into UTC day boundaries', () => {
    expect(startOfDayUtc('2026-11-01')).toBe('2026-11-01T00:00:00Z');
    expect(endOfDayUtc('2026-11-14')).toBe('2026-11-14T23:59:59.999Z');
    expect(startOfDayUtc('')).toBeNull();
    expect(endOfDayUtc('not a date')).toBeNull();
  });

  it('flags a from date after the to date', () => {
    const group = new FormGroup(
      { from: new FormControl('2026-11-14'), to: new FormControl('2026-11-01') },
      { validators: dateRangeValidator() },
    );
    expect(group.hasError('dateRange')).toBeTrue();

    group.controls.to.setValue('2026-11-14');
    expect(group.hasError('dateRange')).toBeFalse();
  });
});

describe('templatePlaceholders', () => {
  it('lists each placeholder once, in order', () => {
    expect(templatePlaceholders('Hi {{name}}, {{ trackingNumber }} for {{name}}')).toEqual([
      'name',
      'trackingNumber',
    ]);
    expect(templatePlaceholders('plain')).toEqual([]);
  });
});

describe('toHttpParams', () => {
  it('skips null, undefined and blank values', () => {
    const params = toHttpParams({ a: 'x', b: null, c: undefined, d: ' ', e: 0 });

    expect(params.keys()).toEqual(['a', 'e']);
    expect(params.get('e')).toBe('0');
  });
});

describe('safeReturnUrl', () => {
  it('keeps in-app paths and refuses anything else', () => {
    expect(safeReturnUrl('/admin/webhooks?status=FAILED')).toBe('/admin/webhooks?status=FAILED');
    expect(safeReturnUrl('https://evil.example')).toBe('/admin/notifications');
    expect(safeReturnUrl('//evil.example')).toBe('/admin/notifications');
    expect(safeReturnUrl('/login')).toBe('/admin/notifications');
    expect(safeReturnUrl(undefined)).toBe('/admin/notifications');
  });
});
