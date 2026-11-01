import { TestBed } from '@angular/core/testing';
import { AppError } from '../../../core/http/app-error';
import { ErrorAlert } from './error-alert';

describe('ErrorAlert', () => {
  function create(error: AppError | null, inputs: Record<string, unknown> = {}) {
    const fixture = TestBed.createComponent(ErrorAlert);
    fixture.componentRef.setInput('error', error);
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('renders nothing without an error', () => {
    expect(create(null).element.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows the message and the support reference', () => {
    const { element } = create(
      new AppError(500, 'INTERNAL_ERROR', 'Something went wrong on our side.', [], 'corr-9'),
    );

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Something went wrong on our side.',
    );
    expect(element.textContent).toContain('corr-9');
  });

  it('lists validation details on request', () => {
    const error = new AppError(400, 'VALIDATION_FAILED', 'Invalid', [
      { field: 'city', message: 'must not be blank' },
    ]);

    expect(create(error).element.textContent).not.toContain('must not be blank');
    expect(create(error, { showDetails: true }).element.textContent).toContain(
      'city: must not be blank',
    );
  });

  it('offers a retry that emits', () => {
    const { fixture, element } = create(new AppError(503, 'X', 'Down'), { retryable: true });
    let retries = 0;
    fixture.componentInstance.retry.subscribe(() => retries++);

    element.querySelector('button')?.click();

    expect(retries).toBe(1);
  });
});
