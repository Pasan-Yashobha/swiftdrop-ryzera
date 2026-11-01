import { Pipe, PipeTransform } from '@angular/core';
import { Money } from '../../core/models/order.model';

const AMOUNT_FORMAT = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * Formats money as `LKR 1,250.00`. Accepts a Money object or a bare amount (default currency LKR).
 * The currency code is printed as is, so the output does not depend on the browser locale.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: Money | number | null | undefined, currency = 'LKR'): string {
    if (value === null || value === undefined) {
      return '';
    }
    const money = typeof value === 'number' ? { amount: value, currency } : value;
    return `${money.currency} ${AMOUNT_FORMAT.format(money.amount)}`;
  }
}
