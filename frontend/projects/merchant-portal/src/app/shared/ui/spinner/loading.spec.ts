import { TestBed } from '@angular/core/testing';
import { Skeleton } from './skeleton';
import { Spinner } from './spinner';

describe('Spinner', () => {
  it('shows its label in a status region', () => {
    const fixture = TestBed.createComponent(Spinner);
    fixture.componentRef.setInput('label', 'Loading orders');
    fixture.detectChanges();
    const status = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');

    expect(status?.textContent).toContain('Loading orders');
  });
});

describe('Skeleton', () => {
  it('renders the requested number of placeholder lines and announces the label once', () => {
    const fixture = TestBed.createComponent(Skeleton);
    fixture.componentRef.setInput('lines', 4);
    fixture.componentRef.setInput('label', 'Loading profile');
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll('.skeleton__line').length).toBe(4);
    expect(element.querySelector('.visually-hidden')?.textContent).toBe('Loading profile');
  });
});
