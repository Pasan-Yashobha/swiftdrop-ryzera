import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  input,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../../../core/api/order.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { MerchantStore } from '../../../core/merchant/merchant.store';
import { CreateOrderRequest, Order, PARCEL_SIZES } from '../../../core/models/order.model';
import { focusFirstInvalid } from '../../../shared/forms/focus-first-invalid';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CopyButton } from '../../../shared/ui/copy-button/copy-button';
import { FieldControl, FormField } from '../../../shared/ui/form-field/form-field';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { focusAfterRender } from '../../../shared/util/focus';
import { createOrderForm, ORDER_LIMITS, toCreateOrderRequest } from '../util/order-form';
import { PARCEL_SIZE_LABELS } from '../util/order-labels';
import { DUPLICATE_REF_MESSAGE, describeSubmitError } from '../util/order-submit-problem';

interface SubmitAttempt {
  /** Identifies the exact order and environment that was sent. */
  fingerprint: string;
  idempotencyKey: string;
}

/** Create one order by hand, as a live parcel or as a simulated sandbox order. */
@Component({
  selector: 'app-order-create-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CopyButton,
    FieldControl,
    FormField,
    MoneyPipe,
    StatusBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-create.page.html',
  styleUrl: './order-create.page.css',
})
export class OrderCreatePage implements OnInit {
  /** `?sandbox=true` opens the form in sandbox mode (the dashboard's "send a test order" link). */
  readonly sandbox = input<string>();

  protected readonly form = createOrderForm();
  protected readonly limits = ORDER_LIMITS;
  protected readonly sizeOptions = PARCEL_SIZES.map((size) => ({
    value: size,
    label: PARCEL_SIZE_LABELS[size],
  }));
  protected readonly weightMessages = {
    decimals: 'Use at most 2 decimal places, for example 2.25.',
  };
  protected readonly referenceMessages = {
    pattern: 'Use only letters, numbers and . _ : / # -',
  };

  protected readonly submitting = signal(false);
  protected readonly created = signal<Order | null>(null);
  protected readonly error = signal<AppError | null>(null);
  /** Server messages that belong to no form field. */
  protected readonly unmatchedErrors = signal<string[]>([]);
  protected readonly problem = computed(() => {
    const error = this.error();
    return error ? describeSubmitError(error) : null;
  });

  private readonly api = inject(OrderApi);
  private readonly merchant = inject(MerchantStore).merchant;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly externalRefInput = viewChild<ElementRef<HTMLInputElement>>('externalRefInput');
  private readonly createdHeading = viewChild<ElementRef<HTMLElement>>('createdHeading');
  /**
   * The Idempotency-Key of the latest submission. Submitting the same order again (a retry after a timeout or a
   * 503) reuses it, so the server cannot create the order twice; changing anything starts a new attempt.
   */
  private attempt: SubmitAttempt | null = null;

  ngOnInit(): void {
    this.resetForm(this.sandbox() === 'true');
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    const request = toCreateOrderRequest(this.form.getRawValue());
    if (this.form.invalid || !request) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }
    const sandbox = this.form.controls.sandbox.value;
    this.submitting.set(true);
    this.error.set(null);
    this.unmatchedErrors.set([]);
    this.api
      .create(request, { sandbox, idempotencyKey: this.idempotencyKeyFor(request, sandbox) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => this.succeed(order),
        error: (error: unknown) => this.fail(toAppError(error)),
      });
  }

  protected createAnother(): void {
    this.created.set(null);
    this.resetForm(this.form.controls.sandbox.value);
  }

  private idempotencyKeyFor(request: CreateOrderRequest, sandbox: boolean): string {
    const fingerprint = JSON.stringify({ request, sandbox });
    if (this.attempt?.fingerprint !== fingerprint) {
      this.attempt = { fingerprint, idempotencyKey: this.api.newIdempotencyKey() };
    }
    return this.attempt.idempotencyKey;
  }

  private succeed(order: Order): void {
    this.attempt = null;
    this.submitting.set(false);
    this.created.set(order);
    focusAfterRender(this.injector, () => this.createdHeading()?.nativeElement);
  }

  private fail(error: AppError): void {
    this.submitting.set(false);
    this.error.set(error);
    if (error.code === 'DUPLICATE_EXTERNAL_REF') {
      const reference = this.form.controls.externalRef;
      reference.setErrors({ server: DUPLICATE_REF_MESSAGE });
      reference.markAsTouched();
      this.externalRefInput()?.nativeElement.focus();
    } else {
      this.unmatchedErrors.set(applyServerErrors(this.form, error));
    }
  }

  /** Empty form; the pickup address starts as the business address because most parcels leave from there. */
  private resetForm(sandbox: boolean): void {
    const merchant = this.merchant();
    this.form.reset({
      pickup: { addressLine: merchant?.addressLine ?? '', city: merchant?.city ?? '' },
      sandbox,
    });
  }
}
