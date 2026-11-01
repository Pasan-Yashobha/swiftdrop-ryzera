import { TestBed } from '@angular/core/testing';
import { EnvironmentBadge } from './environment-badge';

describe('EnvironmentBadge', () => {
  function render(environment: 'LIVE' | 'TEST'): HTMLElement {
    const fixture = TestBed.createComponent(EnvironmentBadge);
    fixture.componentRef.setInput('environment', environment);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('.env') as HTMLElement;
  }

  it('spells out LIVE with the production styling', () => {
    const badge = render('LIVE');

    expect(badge.textContent).toBe('LIVE');
    expect(badge.classList).toContain('env--live');
  });

  it('spells out TEST without the production styling', () => {
    const badge = render('TEST');

    expect(badge.textContent).toBe('TEST');
    expect(badge.classList).not.toContain('env--live');
  });
});
