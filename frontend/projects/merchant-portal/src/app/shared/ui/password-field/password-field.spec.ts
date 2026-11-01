import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { PasswordField } from './password-field';

describe('PasswordField', () => {
  function create() {
    const control = new FormControl('Secret123', { nonNullable: true });
    const fixture = TestBed.createComponent(PasswordField);
    fixture.componentRef.setInput('control', control);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      control,
      input: element.querySelector('input') as HTMLInputElement,
      toggle: element.querySelector('button') as HTMLButtonElement,
    };
  }

  it('hides the password by default and binds to the control', () => {
    const { input, control } = create();

    expect(input.type).toBe('password');
    expect(input.value).toBe('Secret123');
    input.value = 'Changed1';
    input.dispatchEvent(new Event('input'));
    expect(control.value).toBe('Changed1');
  });

  it('toggles visibility and tells assistive technology the state', () => {
    const { fixture, input, toggle } = create();
    expect(toggle.getAttribute('aria-pressed')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    expect(input.type).toBe('text');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(toggle.textContent).toContain('Hide');

    toggle.click();
    fixture.detectChanges();
    expect(input.type).toBe('password');
  });

  it('is a labelled field', () => {
    const { fixture, input } = create();
    const label = (fixture.nativeElement as HTMLElement).querySelector('label') as HTMLLabelElement;

    expect(label.htmlFor).toBe(input.id);
    expect(label.textContent).toContain('Password');
  });
});
