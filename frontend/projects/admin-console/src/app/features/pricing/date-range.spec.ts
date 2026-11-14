import { FormControl, FormGroup } from '@angular/forms';
import { endAfterStart, today } from './date-range';

describe('endAfterStart', () => {
  const period = (validFrom: string, validTo: string) =>
    new FormGroup(
      { validFrom: new FormControl(validFrom), validTo: new FormControl(validTo) },
      { validators: endAfterStart },
    );

  it('accepts an open-ended period', () => {
    expect(period('2026-11-01', '').valid).toBeTrue();
  });

  it('accepts an end after the start', () => {
    expect(period('2026-11-01', '2026-12-01').valid).toBeTrue();
  });

  it('rejects an end on or before the start, because the end is exclusive', () => {
    expect(period('2026-11-01', '2026-11-01').hasError('endBeforeStart')).toBeTrue();
    expect(period('2026-11-01', '2026-10-31').hasError('endBeforeStart')).toBeTrue();
  });

  it('formats today as yyyy-mm-dd', () => {
    expect(today()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
