import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EmptyState } from './empty-state';

@Component({
  imports: [EmptyState],
  template: `<app-empty-state heading="No orders yet" message="Create your first order."
    ><button>Create</button></app-empty-state
  >`,
})
class Host {}

describe('EmptyState', () => {
  it('shows the heading, message and projected action', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h2')?.textContent).toBe('No orders yet');
    expect(element.textContent).toContain('Create your first order.');
    expect(element.querySelector('button')?.textContent).toBe('Create');
  });

  it('omits the message when none is given', () => {
    const fixture = TestBed.createComponent(EmptyState);
    fixture.componentRef.setInput('heading', 'Nothing here');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('p')).toBeNull();
  });
});
