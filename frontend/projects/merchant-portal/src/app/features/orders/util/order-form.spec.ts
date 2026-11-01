import { createOrderForm, OrderForm, toCreateOrderRequest } from './order-form';

function validForm(): OrderForm {
  const form = createOrderForm();
  form.setValue({
    externalRef: 'ORDER-1001',
    recipient: { name: 'Nimali Perera', phone: '+94 77 123 4567' },
    pickup: { addressLine: '12 Galle Rd', city: 'Colombo', postalCode: '00300' },
    dropoff: { addressLine: '45 Temple St', city: 'Kandy', postalCode: '' },
    parcel: { weightKg: 2.5, size: 'SMALL', description: 'Phone case' },
    notes: '',
    sandbox: false,
  });
  return form;
}

describe('createOrderForm', () => {
  it('requires the recipient, both addresses and the parcel, and nothing else', () => {
    const form = createOrderForm();

    expect(form.invalid).toBeTrue();
    const invalid = [
      form.controls.recipient.controls.name,
      form.controls.recipient.controls.phone,
      form.controls.pickup.controls.addressLine,
      form.controls.pickup.controls.city,
      form.controls.dropoff.controls.addressLine,
      form.controls.dropoff.controls.city,
      form.controls.parcel.controls.weightKg,
      form.controls.parcel.controls.size,
    ];
    expect(invalid.every((control) => control.hasError('required'))).toBeTrue();
    expect(form.controls.externalRef.valid).toBeTrue();
    expect(form.controls.notes.valid).toBeTrue();
    expect(form.controls.pickup.controls.postalCode.valid).toBeTrue();
    expect(form.controls.parcel.controls.description.valid).toBeTrue();
    expect(validForm().valid).toBeTrue();
  });

  it('treats whitespace-only text as missing', () => {
    const form = validForm();

    form.controls.recipient.controls.name.setValue('   ');

    expect(form.controls.recipient.controls.name.hasError('required')).toBeTrue();
  });

  it('applies the backend length limits to the trimmed text', () => {
    const form = validForm();
    const { recipient, pickup, parcel } = form.controls;

    recipient.controls.name.setValue('n'.repeat(121));
    pickup.controls.addressLine.setValue('a'.repeat(301));
    pickup.controls.city.setValue('c'.repeat(101));
    pickup.controls.postalCode.setValue('1'.repeat(17));
    parcel.controls.description.setValue('d'.repeat(201));
    form.controls.notes.setValue('n'.repeat(501));
    form.controls.externalRef.setValue('r'.repeat(101));

    for (const control of [
      recipient.controls.name,
      pickup.controls.addressLine,
      pickup.controls.city,
      pickup.controls.postalCode,
      parcel.controls.description,
      form.controls.notes,
      form.controls.externalRef,
    ]) {
      expect(control.hasError('maxlength')).withContext(JSON.stringify(control.errors)).toBeTrue();
    }

    recipient.controls.name.setValue(`  ${'n'.repeat(120)}  `);
    expect(recipient.controls.name.valid).toBeTrue();
  });

  it('accepts phone numbers with separators and rejects other formats', () => {
    const form = validForm();
    const phone = form.controls.recipient.controls.phone;

    phone.setValue('+94 (77) 123-4567');
    expect(phone.valid).toBeTrue();

    for (const bad of ['12345', '+9477123456789012', 'call me', '077-ABC-4567']) {
      phone.setValue(bad);
      expect(phone.hasError('phone')).withContext(bad).toBeTrue();
    }
  });

  it('limits the weight to 0.01..50 kg with at most two decimals', () => {
    const form = validForm();
    const weight = form.controls.parcel.controls.weightKg;

    for (const good of [0.01, 2.35, 50, 49.99]) {
      weight.setValue(good);
      expect(weight.valid).withContext(String(good)).toBeTrue();
    }

    weight.setValue(0);
    expect(weight.hasError('min')).toBeTrue();
    weight.setValue(50.01);
    expect(weight.hasError('max')).toBeTrue();
    weight.setValue(1.005);
    expect(weight.hasError('decimals')).toBeTrue();
    weight.setValue(null);
    expect(weight.hasError('required')).toBeTrue();
  });

  it('only allows the characters the backend accepts in the external reference', () => {
    const form = validForm();
    const ref = form.controls.externalRef;

    ref.setValue(' Order.1001:A/B#9_x-y ');
    expect(ref.valid).toBeTrue();

    for (const bad of ['has space', 'emoji🙂', 'semi;colon', 'q?']) {
      ref.setValue(bad);
      expect(ref.hasError('pattern')).withContext(bad).toBeTrue();
    }
  });

  it('rejects control characters in every free-text field', () => {
    const form = validForm();

    form.controls.notes.setValue('line one\nline two');
    form.controls.recipient.controls.name.setValue('Nimali\u0000');

    expect(form.controls.notes.hasError('controlChars')).toBeTrue();
    expect(form.controls.recipient.controls.name.hasError('controlChars')).toBeTrue();
  });
});

describe('toCreateOrderRequest', () => {
  it('builds the request: trimmed, phone normalised, blank optional fields omitted', () => {
    const form = validForm();
    form.controls.recipient.controls.name.setValue('  Nimali Perera ');

    expect(toCreateOrderRequest(form.getRawValue())).toEqual({
      externalRef: 'ORDER-1001',
      recipient: { name: 'Nimali Perera', phone: '+94771234567' },
      pickup: { addressLine: '12 Galle Rd', city: 'Colombo', postalCode: '00300' },
      dropoff: { addressLine: '45 Temple St', city: 'Kandy', postalCode: undefined },
      parcel: { weightKg: 2.5, size: 'SMALL', description: 'Phone case' },
      notes: undefined,
    });
  });

  it('leaves out an empty external reference', () => {
    const form = validForm();
    form.controls.externalRef.setValue('  ');

    expect(toCreateOrderRequest(form.getRawValue())?.externalRef).toBeUndefined();
  });

  it('returns null while the weight or size is missing', () => {
    const form = validForm();
    form.controls.parcel.controls.size.setValue('');

    expect(toCreateOrderRequest(form.getRawValue())).toBeNull();

    form.controls.parcel.controls.size.setValue('LARGE');
    form.controls.parcel.controls.weightKg.setValue(null);

    expect(toCreateOrderRequest(form.getRawValue())).toBeNull();
  });
});
