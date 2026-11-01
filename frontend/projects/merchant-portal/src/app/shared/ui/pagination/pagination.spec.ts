import { TestBed } from '@angular/core/testing';
import { PageInfo } from '../../../core/models/page.model';
import { Pagination } from './pagination';

describe('Pagination', () => {
  function render(info: PageInfo) {
    const fixture = TestBed.createComponent(Pagination);
    fixture.componentRef.setInput('info', info);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('button'));
    return { fixture, element, previous: buttons[0], next: buttons[1] };
  }

  it('summarises the visible range', () => {
    const { element } = render({ page: 1, size: 20, totalElements: 45, totalPages: 3 });

    expect(element.textContent).toContain('Showing 21-40 of 45');
    expect(element.textContent).toContain('Page 2 of 3');
  });

  it('caps the range at the total on the last page', () => {
    const { element } = render({ page: 2, size: 20, totalElements: 45, totalPages: 3 });

    expect(element.textContent).toContain('Showing 41-45 of 45');
  });

  it('emits the zero-based page for next and previous', () => {
    const { fixture, previous, next } = render({
      page: 1,
      size: 20,
      totalElements: 45,
      totalPages: 3,
    });
    const pages: number[] = [];
    fixture.componentInstance.pageChange.subscribe((page) => pages.push(page));

    next.click();
    previous.click();

    expect(pages).toEqual([2, 0]);
  });

  it('disables previous on the first page and next on the last', () => {
    const first = render({ page: 0, size: 20, totalElements: 45, totalPages: 3 });
    const last = render({ page: 2, size: 20, totalElements: 45, totalPages: 3 });

    expect(first.previous.disabled).toBeTrue();
    expect(first.next.disabled).toBeFalse();
    expect(last.next.disabled).toBeTrue();
  });

  it('renders nothing when there are no results', () => {
    const { element } = render({ page: 0, size: 20, totalElements: 0, totalPages: 0 });

    expect(element.querySelector('nav')).toBeNull();
  });
});
