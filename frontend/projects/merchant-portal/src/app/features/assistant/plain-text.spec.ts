import { plainText } from './plain-text';

describe('plainText', () => {
  it('removes bold and italic markers', () => {
    expect(plainText('Your parcel is **Out for delivery**.\n\n*(Note: sample data.)*')).toBe(
      'Your parcel is Out for delivery.\n\n(Note: sample data.)',
    );
  });

  it('turns Markdown bullets into plain bullets', () => {
    expect(plainText('* SD-1 delivered\n- SD-2 in transit')).toBe(
      '• SD-1 delivered\n• SD-2 in transit',
    );
  });

  it('removes headings and code marks', () => {
    expect(plainText('## Fees\nUse `SAVE10`')).toBe('Fees\nUse SAVE10');
  });

  it('leaves arithmetic alone', () => {
    expect(plainText('2 * 3 = 6 LKR')).toBe('2 * 3 = 6 LKR');
  });
});
