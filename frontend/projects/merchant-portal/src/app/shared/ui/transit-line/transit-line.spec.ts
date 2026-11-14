import { TestBed } from '@angular/core/testing';
import { TrackingStop } from '../../../core/models/tracking.model';
import { stopsAt } from '../../../core/testing/tracking-data';
import { TransitLine } from './transit-line';

describe('TransitLine', () => {
  function render(stops: TrackingStop[], variant: 'board' | 'light' = 'board'): HTMLElement {
    const fixture = TestBed.createComponent(TransitLine);
    fixture.componentRef.setInput('stops', stops);
    fixture.componentRef.setInput('variant', variant);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('draws the five numbered stops with their state', () => {
    const element = render(stopsAt(4));
    const stops = Array.from(element.querySelectorAll('li'));

    expect(stops.length).toBe(5);
    expect(stops.map((stop) => stop.getAttribute('data-state'))).toEqual([
      'DONE',
      'DONE',
      'DONE',
      'CURRENT',
      'PENDING',
    ]);
    expect(stops[0].querySelector('.dot')?.textContent?.trim()).toBe('01');
    expect(stops[3].getAttribute('aria-current')).toBe('step');
    expect(stops[4].getAttribute('aria-current')).toBeNull();
    expect(stops[3].textContent).toContain('Out for delivery');
    expect(stops[3].textContent).toContain('current stop');
    expect(stops[4].querySelector('.time')?.textContent?.trim()).toBe('');
  });

  it('marks a failed stop with a cross instead of its number', () => {
    const stops = stopsAt(4);
    stops[3] = { ...stops[3], status: 'FAILED', label: 'Delivery attempt failed', state: 'EXCEPTION' };

    const failed = render(stops).querySelectorAll('li')[3];

    expect(failed.querySelector('.dot')?.textContent?.trim()).toBe('✕');
    expect(failed.getAttribute('aria-current')).toBe('step');
    expect(failed.textContent).toContain('problem');
  });

  it('has a light variant for cards', () => {
    expect(render(stopsAt(2), 'light').querySelector('ol')?.classList).toContain('line--light');
  });
});
