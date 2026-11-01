import { FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import {
  CreateOrderAddress,
  CreateOrderRequest,
  ParcelSize,
} from '../../../core/models/order.model';
import { noControlCharacters, notBlank, phoneNumber } from '../../../shared/forms/validators';
import { normalizePhone } from '../../../shared/util/phone';

/** Limits of CreateOrderRequest (CONTRACT 5.4); the backend trims text before it counts. */
export const ORDER_LIMITS = {
  recipientName: 120,
  addressLine: 300,
  city: 100,
  postalCode: 16,
  description: 200,
  notes: 500,
  externalRef: 100,
  minWeightKg: 0.01,
  maxWeightKg: 50,
} as const;

const EXTERNAL_REF_FORMAT = /^[A-Za-z0-9._:/#-]+$/;

/** Counts characters after trimming, like the backend does. Reports the standard `maxlength` error. */
function maxTrimmedLength(max: number): ValidatorFn {
  return (control): ValidationErrors | null => {
    const length = typeof control.value === 'string' ? control.value.trim().length : 0;
    return length > max ? { maxlength: { requiredLength: max, actualLength: length } } : null;
  };
}

const externalRefFormat: ValidatorFn = (control) => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value === '' || EXTERNAL_REF_FORMAT.test(value) ? null : { pattern: true };
};

const twoDecimals: ValidatorFn = (control) =>
  typeof control.value !== 'number' || Math.round(control.value * 100) / 100 === control.value
    ? null
    : { decimals: true };

function text(...validators: ValidatorFn[]): FormControl<string> {
  return new FormControl('', {
    nonNullable: true,
    validators: [...validators, noControlCharacters],
  });
}

function addressGroup() {
  return new FormGroup({
    addressLine: text(notBlank, maxTrimmedLength(ORDER_LIMITS.addressLine)),
    city: text(notBlank, maxTrimmedLength(ORDER_LIMITS.city)),
    postalCode: text(maxTrimmedLength(ORDER_LIMITS.postalCode)),
  });
}

/** The create-order form with the backend's validation rules. `sandbox` is a submit option, not part of the order. */
export function createOrderForm() {
  return new FormGroup({
    externalRef: text(maxTrimmedLength(ORDER_LIMITS.externalRef), externalRefFormat),
    recipient: new FormGroup({
      name: text(notBlank, maxTrimmedLength(ORDER_LIMITS.recipientName)),
      phone: text(notBlank, phoneNumber),
    }),
    pickup: addressGroup(),
    dropoff: addressGroup(),
    parcel: new FormGroup({
      weightKg: new FormControl<number | null>(null, [
        Validators.required,
        Validators.min(ORDER_LIMITS.minWeightKg),
        Validators.max(ORDER_LIMITS.maxWeightKg),
        twoDecimals,
      ]),
      size: new FormControl<ParcelSize | ''>('', {
        nonNullable: true,
        validators: Validators.required,
      }),
      description: text(maxTrimmedLength(ORDER_LIMITS.description)),
    }),
    notes: text(maxTrimmedLength(ORDER_LIMITS.notes)),
    sandbox: new FormControl(false, { nonNullable: true }),
  });
}

export type OrderForm = ReturnType<typeof createOrderForm>;
export type OrderFormValue = ReturnType<OrderForm['getRawValue']>;

/** Trimmed text, or undefined when blank so optional fields are left out of the request. */
function optional(value: string): string | undefined {
  return value.trim() || undefined;
}

function toAddress(value: OrderFormValue['pickup']): CreateOrderAddress {
  return {
    addressLine: value.addressLine.trim(),
    city: value.city.trim(),
    postalCode: optional(value.postalCode),
  };
}

/** The API request for a completed form, or null while the weight or size is still missing. */
export function toCreateOrderRequest(value: OrderFormValue): CreateOrderRequest | null {
  const { weightKg, size, description } = value.parcel;
  if (weightKg === null || size === '') {
    return null;
  }
  return {
    externalRef: optional(value.externalRef),
    recipient: { name: value.recipient.name.trim(), phone: normalizePhone(value.recipient.phone) },
    pickup: toAddress(value.pickup),
    dropoff: toAddress(value.dropoff),
    parcel: { weightKg, size, description: optional(description) },
    notes: optional(value.notes),
  };
}
