import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { openTrackingPage, QUICK_TRACK_HINT } from './quick-track';

describe('openTrackingPage (Quick Track boxes)', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    router = TestBed.inject(Router);
  });

  it('opens /track/<number> with the number in canonical form', () => {
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);

    expect(openTrackingPage(router, '  sd-2641-7788 ')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/track', 'SD-2641-7788']);
  });

  it('returns a hint and stays put for anything that is not a tracking number', () => {
    const navigate = spyOn(router, 'navigate');

    expect(openTrackingPage(router, 'hello world')).toBe(QUICK_TRACK_HINT);
    expect(openTrackingPage(router, '')).toBe(QUICK_TRACK_HINT);
    expect(openTrackingPage(router, null)).toBe(QUICK_TRACK_HINT);
    expect(navigate).not.toHaveBeenCalled();
  });
});
