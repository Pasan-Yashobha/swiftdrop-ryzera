import { generateId, shortId } from './id';
import { isValidPhone, normalizePhone } from './phone';

describe('phone helpers', () => {
  it('strips spaces, dashes and brackets', () => {
    expect(normalizePhone('+94 (77) 123-4567')).toBe('+94771234567');
  });

  it('accepts 9 to 15 digits with an optional leading plus', () => {
    expect(isValidPhone('0771234567')).toBeTrue();
    expect(isValidPhone('+94 77 123 4567')).toBeTrue();
    expect(isValidPhone('+123456789012345')).toBeTrue();
  });

  it('rejects numbers that are too short, too long or contain letters', () => {
    expect(isValidPhone('12345678')).toBeFalse();
    expect(isValidPhone('1234567890123456')).toBeFalse();
    expect(isValidPhone('077-ABC-4567')).toBeFalse();
    expect(isValidPhone('')).toBeFalse();
  });
});

describe('id helpers', () => {
  it('generates RFC 4122 version 4 ids', () => {
    expect(generateId()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
  });

  it('generates a valid id even where crypto.randomUUID is missing', () => {
    // Shadow the prototype method on the instance, as in a non-secure (plain http) context.
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    try {
      expect(generateId()).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
      );
    } finally {
      delete (crypto as { randomUUID?: unknown }).randomUUID;
    }
  });

  it('shortens an id to its first segment', () => {
    expect(shortId('d4c1a1f2-0000-4000-8000-000000000001')).toBe('d4c1a1f2');
  });
});
