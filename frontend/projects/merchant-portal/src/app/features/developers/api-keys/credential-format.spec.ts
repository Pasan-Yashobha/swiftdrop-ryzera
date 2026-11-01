import { countActive, describeExpiry } from './credential-format';

describe('countActive', () => {
  it('counts only active credentials', () => {
    expect(countActive([{ status: 'ACTIVE' }, { status: 'REVOKED' }, { status: 'ACTIVE' }])).toBe(
      2,
    );
  });

  it('is zero for an empty list', () => {
    expect(countActive([])).toBe(0);
  });
});

describe('describeExpiry', () => {
  const now = Date.parse('2026-11-01T08:00:00Z');
  const after = (ms: number) => new Date(now + ms).toISOString();
  const hour = 3_600_000;

  it('has nothing to say about a key that does not expire', () => {
    expect(describeExpiry(null, now)).toBeNull();
  });

  it('ignores a timestamp it cannot read', () => {
    expect(describeExpiry('soon', now)).toBeNull();
  });

  it('shows the whole hours left during a rotation grace period', () => {
    expect(describeExpiry(after(23 * hour + 40 * 60_000), now)).toBe('expires in 23h');
  });

  it('shows minutes in the last hour, never less than one', () => {
    expect(describeExpiry(after(25 * 60_000), now)).toBe('expires in 25m');
    expect(describeExpiry(after(5_000), now)).toBe('expires in 1m');
  });

  it('switches to days beyond two days', () => {
    expect(describeExpiry(after(72 * hour), now)).toBe('expires in 3d');
    expect(describeExpiry(after(47 * hour), now)).toBe('expires in 47h');
  });

  it('reports a key whose grace period is over', () => {
    expect(describeExpiry(after(-1), now)).toBe('expired');
    expect(describeExpiry(after(0), now)).toBe('expired');
  });
});
