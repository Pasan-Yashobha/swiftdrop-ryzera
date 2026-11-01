import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { notBlank } from '../../forms/validators';
import { FieldControl, FormField } from './form-field';

@Component({
  imports: [ReactiveFormsModule, FormField, FieldControl],
  template: `
    <app-form-field label="Email" hint="We never share it" [control]="email" [messages]="messages">
      <input appFieldControl type="email" [formControl]="email" />
    </app-form-field>
  `,
})
class Host {
  email = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.email],
  });
  messages: Record<string, string> = {};
}

describe('FormField', () => {
  function create() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      host: fixture.componentInstance,
      element,
      input: element.querySelector('input') as HTMLInputElement,
      error: () => element.querySelector('.field__error')?.textContent?.trim() ?? null,
    };
  }

  it('links the label to the input and marks required fields', () => {
    const { element, input } = create();
    const label = element.querySelector('label') as HTMLLabelElement;

    expect(label.htmlFor).toBe(input.id);
    expect(label.textContent).toContain('Email');
    expect(label.textContent).toContain('(required)');
  });

  it('shows the hint and links it to the input', () => {
    const { element, input } = create();

    expect(element.textContent).toContain('We never share it');
    expect(input.getAttribute('aria-describedby')).toBeTruthy();
    expect(input.hasAttribute('aria-invalid')).toBeFalse();
  });

  it('stays quiet until the control has been touched', () => {
    const { fixture, error } = create();

    fixture.detectChanges();

    expect(error()).toBeNull();
  });

  it('shows the validation message once touched and flags the input as invalid', () => {
    const { fixture, host, input, error } = create();

    host.email.markAsTouched();
    fixture.detectChanges();

    expect(error()).toBe('Email is required.');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toContain(`${input.id}-error`);
  });

  it('updates as the user types', () => {
    const { fixture, host, input, error } = create();
    host.email.markAsTouched();

    host.email.setValue('nope');
    fixture.detectChanges();
    expect(error()).toBe('Enter a valid email address.');

    host.email.setValue('a@b.lk');
    fixture.detectChanges();
    expect(error()).toBeNull();
    expect(input.hasAttribute('aria-invalid')).toBeFalse();
  });

  it('prefers a message override', () => {
    const { fixture, host, error } = create();
    host.messages = { required: 'Tell us your email.' };
    host.email.markAsTouched();
    fixture.detectChanges();

    expect(error()).toBe('Tell us your email.');
  });

  it('shows a server-side message set on the control', () => {
    const { fixture, host, error } = create();
    host.email.setValue('a@b.lk');
    host.email.setErrors({ server: 'Email is already registered' });
    host.email.markAsTouched();
    fixture.detectChanges();

    expect(error()).toBe('Email is already registered');
  });

  it('treats the notBlank validator as required too', () => {
    const fixture = TestBed.createComponent(FormField);
    fixture.componentRef.setInput('label', 'City');
    fixture.componentRef.setInput('control', new FormControl('', notBlank));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('label')?.textContent).toContain(
      '(required)',
    );
  });

  it('marks fields without a required validator as optional', () => {
    const fixture = TestBed.createComponent(FormField);
    fixture.componentRef.setInput('label', 'Website');
    fixture.componentRef.setInput('control', new FormControl(''));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('label')?.textContent).toContain(
      '(optional)',
    );
  });
});
