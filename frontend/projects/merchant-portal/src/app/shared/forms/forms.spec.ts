import { FormControl, FormGroup } from '@angular/forms';
import { AppError } from '../../core/http/app-error';
import { applyServerErrors } from './server-errors';
import {
  httpUrl,
  noControlCharacters,
  notBlank,
  passwordPolicy,
  phoneNumber,
  sameAsSibling,
} from './validators';

describe('validators', () => {
  it('notBlank rejects whitespace-only text', () => {
    expect(notBlank(new FormControl('   '))).toEqual({ required: true });
    expect(notBlank(new FormControl(''))).toEqual({ required: true });
    expect(notBlank(new FormControl(' x '))).toBeNull();
  });

  it('phoneNumber accepts empty values and valid numbers only', () => {
    expect(phoneNumber(new FormControl(''))).toBeNull();
    expect(phoneNumber(new FormControl('+94 77 123 4567'))).toBeNull();
    expect(phoneNumber(new FormControl('abc'))).toEqual({ phone: true });
  });

  it('httpUrl accepts empty values and absolute http(s) URLs only', () => {
    expect(httpUrl(new FormControl(''))).toBeNull();
    expect(httpUrl(new FormControl('https://shop.example.com/a?b=1'))).toBeNull();
    expect(httpUrl(new FormControl('http://localhost:8080'))).toBeNull();
    expect(httpUrl(new FormControl('ftp://shop.example.com'))).toEqual({ url: true });
    expect(httpUrl(new FormControl('javascript:alert(1)'))).toEqual({ url: true });
    expect(httpUrl(new FormControl('shop.example.com'))).toEqual({ url: true });
  });

  it('noControlCharacters rejects control characters', () => {
    expect(noControlCharacters(new FormControl('Call on arrival'))).toBeNull();
    expect(noControlCharacters(new FormControl('bad\u0007bell'))).toEqual({ controlChars: true });
  });

  it('passwordPolicy lists the rules that are not met', () => {
    expect(passwordPolicy(new FormControl('Abcdef12'))).toBeNull();
    expect(passwordPolicy(new FormControl('abc'))).toEqual({
      passwordPolicy: { unmet: ['length', 'upper', 'digit'] },
    });
  });

  it('sameAsSibling compares with the named sibling control', () => {
    const form = new FormGroup({
      password: new FormControl('Abcdef12'),
      confirm: new FormControl('Abcdef12', sameAsSibling('password')),
    });
    expect(form.controls.confirm.errors).toBeNull();

    form.controls.password.setValue('Different1');
    form.controls.confirm.updateValueAndValidity();

    expect(form.controls.confirm.errors).toEqual({ mismatch: true });
  });
});

describe('applyServerErrors', () => {
  const error = new AppError(400, 'VALIDATION_FAILED', 'Validation failed', [
    { field: 'email', message: 'must be a well-formed email address' },
    { field: 'dropoff.city', message: 'must not be blank' },
    { field: 'unknown', message: 'something else' },
  ]);

  it('puts messages on matching controls (including nested paths) and returns the rest', () => {
    const form = new FormGroup({
      email: new FormControl(''),
      dropoff: new FormGroup({ city: new FormControl('') }),
    });

    const unmatched = applyServerErrors(form, error);

    expect(form.controls.email.errors).toEqual({ server: 'must be a well-formed email address' });
    expect(form.controls.email.touched).toBeTrue();
    expect(form.controls.dropoff.controls.city.errors).toEqual({ server: 'must not be blank' });
    expect(unmatched).toEqual(['unknown: something else']);
  });

  it('keeps existing client-side errors and clears the server error when the user edits the field', () => {
    const control = new FormControl('', notBlank);
    const form = new FormGroup({ email: control });

    applyServerErrors(form, new AppError(400, 'X', 'x', [{ field: 'email', message: 'taken' }]));
    expect(control.errors).toEqual({ required: true, server: 'taken' });

    control.setValue('a@b.lk');

    expect(control.errors).toBeNull();
  });
});
