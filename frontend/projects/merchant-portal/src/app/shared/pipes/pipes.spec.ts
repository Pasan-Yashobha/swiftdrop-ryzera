import { MoneyPipe } from './money.pipe';
import { RelativeTimePipe } from './relative-time.pipe';

describe('MoneyPipe', () => {
  const pipe = new MoneyPipe();

  it('formats a Money object with its currency', () => {
    expect(pipe.transform({ amount: 1250, currency: 'LKR' })).toBe('LKR 1,250.00');
  });

  it('formats a bare number as LKR with two decimals', () => {
    expect(pipe.transform(450)).toBe('LKR 450.00');
    expect(pipe.transform(1234567.5)).toBe('LKR 1,234,567.50');
  });

  it('accepts another currency for bare numbers', () => {
    expect(pipe.transform(10, 'USD')).toBe('USD 10.00');
  });

  it('renders nothing for missing values', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});

describe('RelativeTimePipe', () => {
  const pipe = new RelativeTimePipe();
  const now = Date.parse('2026-11-01T12:00:00Z');

  it('says "just now" for the last few seconds', () => {
    expect(pipe.transform('2026-11-01T11:59:40Z', now)).toBe('just now');
  });

  it('describes minutes, hours and days in the past', () => {
    expect(pipe.transform('2026-11-01T11:55:00Z', now)).toBe('5 minutes ago');
    expect(pipe.transform('2026-11-01T09:00:00Z', now)).toBe('3 hours ago');
    expect(pipe.transform('2026-10-29T12:00:00Z', now)).toBe('3 days ago');
    expect(pipe.transform('2026-10-31T12:00:00Z', now)).toBe('yesterday');
  });

  it('describes the future', () => {
    expect(pipe.transform('2026-11-01T14:00:00Z', now)).toBe('in 2 hours');
  });

  it('uses months and years for older dates', () => {
    expect(pipe.transform('2026-08-01T12:00:00Z', now)).toBe('3 months ago');
    expect(pipe.transform('2024-11-01T12:00:00Z', now)).toBe('2 years ago');
  });

  it('renders nothing for missing or invalid values', () => {
    expect(pipe.transform(null, now)).toBe('');
    expect(pipe.transform('not a date', now)).toBe('');
  });
});
