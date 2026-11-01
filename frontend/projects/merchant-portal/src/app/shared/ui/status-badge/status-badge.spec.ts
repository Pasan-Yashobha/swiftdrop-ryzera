import { TestBed } from '@angular/core/testing';
import { StatusBadge, statusPresentation } from './status-badge';

describe('StatusBadge', () => {
  function render(status: string): HTMLElement {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('spells the status out as text, with a symbol that is hidden from screen readers', () => {
    const element = render('OUT_FOR_DELIVERY');

    expect(element.textContent).toContain('Out for delivery');
    expect(element.querySelector('.badge__icon')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('uses a different tone and symbol per outcome so colour is not the only signal', () => {
    const delivered = render('DELIVERED');
    const failed = render('FAILED');

    expect(delivered.querySelector('.badge--success')).not.toBeNull();
    expect(failed.querySelector('.badge--danger')).not.toBeNull();
    expect(delivered.querySelector('.badge__icon')?.textContent).not.toBe(
      failed.querySelector('.badge__icon')?.textContent,
    );
  });

  it('covers merchant and credential statuses', () => {
    expect(statusPresentation('APPROVED')).toEqual({ label: 'Approved', tone: 'success' });
    expect(statusPresentation('REJECTED').tone).toBe('danger');
    expect(statusPresentation('SUSPENDED').tone).toBe('danger');
    expect(statusPresentation('PENDING').tone).toBe('warning');
    expect(statusPresentation('ACTIVE').tone).toBe('success');
    expect(statusPresentation('REVOKED').tone).toBe('neutral');
  });

  it('shows unknown statuses neutrally in readable form', () => {
    expect(statusPresentation('WAITING_FOR_PICKUP')).toEqual({
      label: 'Waiting for pickup',
      tone: 'neutral',
    });
  });
});
