import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AppError } from '../../core/http/app-error';
import { Merchant, MerchantProfileRequest } from '../../core/models/merchant.model';
import { focusFirstInvalid } from '../../shared/forms/focus-first-invalid';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { httpUrl, notBlank, phoneNumber } from '../../shared/forms/validators';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../shared/ui/form-field/form-field';
import { normalizePhone } from '../../shared/util/phone';

/**
 * The business details form used to register a merchant, edit the profile and fix a rejected application.
 * It validates with the backend's rules (CONTRACT 5.1), shows server field errors, and emits the trimmed request.
 * Content projected into the tag (for example a Cancel button) is shown next to the submit button.
 */
@Component({
  selector: 'app-merchant-form',
  imports: [ReactiveFormsModule, FormField, FieldControl, ErrorAlert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './merchant-form.html',
})
export class MerchantForm {
  /** Existing details to edit; leave null to start empty. */
  readonly merchant = input<Merchant | null>(null);
  /** Pre-fills the contact email of a new registration. */
  readonly defaultEmail = input('');
  readonly submitLabel = input('Save');
  readonly busy = input(false);
  /** A failed submit; its field messages are shown next to the matching inputs. */
  readonly submitError = input<AppError | null>(null);
  readonly submitted = output<MerchantProfileRequest>();

  protected readonly form = new FormGroup({
    businessName: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.minLength(2), Validators.maxLength(150)],
    }),
    businessRegNo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(64)],
    }),
    contactEmail: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.email, Validators.maxLength(254)],
    }),
    contactPhone: new FormControl('', { nonNullable: true, validators: [notBlank, phoneNumber] }),
    addressLine: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(255)],
    }),
    city: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(100)],
    }),
    website: new FormControl('', {
      nonNullable: true,
      validators: [httpUrl, Validators.maxLength(255)],
    }),
  });

  protected readonly unmatchedErrors = signal<string[]>([]);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    effect(() => {
      const merchant = this.merchant();
      if (merchant) {
        this.form.reset({
          businessName: merchant.businessName,
          businessRegNo: merchant.businessRegNo ?? '',
          contactEmail: merchant.contactEmail,
          contactPhone: merchant.contactPhone,
          addressLine: merchant.addressLine,
          city: merchant.city,
          website: merchant.website ?? '',
        });
      }
    });
    effect(() => {
      const email = this.defaultEmail();
      if (email && !this.merchant() && !this.form.controls.contactEmail.value) {
        this.form.controls.contactEmail.setValue(email);
      }
    });
    effect(() => {
      const error = this.submitError();
      this.unmatchedErrors.set(error ? applyServerErrors(this.form, error) : []);
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }
    const value = this.form.getRawValue();
    this.submitted.emit({
      businessName: value.businessName.trim(),
      businessRegNo: value.businessRegNo.trim() || null,
      contactEmail: value.contactEmail.trim(),
      contactPhone: normalizePhone(value.contactPhone),
      addressLine: value.addressLine.trim(),
      city: value.city.trim(),
      website: value.website.trim() || null,
    });
  }
}
