import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ToastOutlet } from './toast-outlet';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    service = TestBed.inject(ToastService);
  });

  it('queues toasts of each kind', fakeAsync(() => {
    service.success('Saved');
    service.error('Failed');
    service.info('FYI');

    expect(service.toasts().map((toast) => toast.kind)).toEqual(['success', 'error', 'info']);
    tick(10000);
  }));

  it('removes a toast after its duration', fakeAsync(() => {
    service.success('Saved', 1000);

    tick(999);
    expect(service.toasts().length).toBe(1);
    tick(1);
    expect(service.toasts().length).toBe(0);
  }));

  it('keeps errors on screen longer than successes by default', fakeAsync(() => {
    service.success('ok');
    service.error('bad');

    tick(5000);
    expect(service.toasts().map((toast) => toast.message)).toEqual(['bad']);
    tick(4000);
    expect(service.toasts().length).toBe(0);
  }));

  it('can dismiss a toast early', fakeAsync(() => {
    service.info('one');
    const [toast] = service.toasts();

    service.dismiss(toast.id);

    expect(service.toasts().length).toBe(0);
    tick(10000);
  }));

  it('shows at most four toasts, dropping the oldest', fakeAsync(() => {
    for (let index = 1; index <= 6; index++) {
      service.info(`toast ${index}`);
    }

    expect(service.toasts().map((toast) => toast.message)).toEqual([
      'toast 3',
      'toast 4',
      'toast 5',
      'toast 6',
    ]);
    tick(10000);
  }));
});

describe('ToastOutlet', () => {
  it('renders toasts inside a polite live region and lets the user dismiss them', fakeAsync(() => {
    const service = TestBed.inject(ToastService);
    const fixture = TestBed.createComponent(ToastOutlet);
    const element = fixture.nativeElement as HTMLElement;

    service.success('Profile updated.');
    fixture.detectChanges();

    const region = element.querySelector('[aria-live]');
    expect(region?.getAttribute('aria-live')).toBe('polite');
    expect(region?.textContent).toContain('Profile updated.');

    element.querySelector<HTMLButtonElement>('button[aria-label="Dismiss notification"]')?.click();
    fixture.detectChanges();

    expect(element.querySelectorAll('.toast').length).toBe(0);
    tick(10000);
  }));
});
