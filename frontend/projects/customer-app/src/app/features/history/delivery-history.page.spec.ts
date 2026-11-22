import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ParcelApi } from '../../core/api/parcel.api';
import { AuthService } from '../../core/auth/auth.service';
import { CustomerParcelSummary, ParcelStatus } from '../../core/models/parcel.model';
import { DeliveryHistoryPage } from './delivery-history.page';

describe('DeliveryHistoryPage', () => {
  let component: DeliveryHistoryPage;
  let fixture: ComponentFixture<DeliveryHistoryPage>;
  let parcelApiSpy: jasmine.SpyObj<ParcelApi>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockParcels: CustomerParcelSummary[] = [
    {
      id: 'p-1',
      trackingNumber: 'SD260001001',
      recipientName: 'Kasun Jayawardena',
      pickupCity: 'Colombo 03',
      dropoffCity: 'Kandy',
      weightKg: 2.5,
      size: 'MEDIUM',
      totalFee: 550,
      currency: 'LKR',
      status: 'IN_TRANSIT' as ParcelStatus,
      createdAt: '2026-11-15T08:00:00Z',
      deliveredAt: null,
    },
    {
      id: 'p-2',
      trackingNumber: 'SD260001002',
      recipientName: 'Nimali Perera',
      pickupCity: 'Colombo 07',
      dropoffCity: 'Galle',
      weightKg: 1.0,
      size: 'SMALL',
      totalFee: 650,
      currency: 'LKR',
      status: 'DELIVERED' as ParcelStatus,
      createdAt: '2026-11-14T10:00:00Z',
      deliveredAt: '2026-11-14T15:00:00Z',
    },
  ];

  const mockPageResponse = {
    content: mockParcels,
    page: 0,
    size: 10,
    totalElements: 2,
    totalPages: 1,
  };

  beforeEach(async () => {
    parcelApiSpy = jasmine.createSpyObj<ParcelApi>('ParcelApi', ['list']);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['hasToken', 'logout']);

    parcelApiSpy.list.and.returnValue(of(mockPageResponse));

    await TestBed.configureTestingModule({
      imports: [DeliveryHistoryPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ParcelApi, useValue: parcelApiSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeliveryHistoryPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load delivery records on init', () => {
    expect(component).toBeTruthy();
    expect(parcelApiSpy.list).toHaveBeenCalledWith(
      jasmine.objectContaining({
        page: 0,
        size: 10,
        sort: 'createdAt,desc',
      })
    );
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('should display parcel tracking numbers and formatted status', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('SD260001001');
    expect(text).toContain('SD260001002');
    expect(text).toContain('In Transit');
    expect(text).toContain('Delivered');
  });

  it('should display empty state when no parcels match', () => {
    parcelApiSpy.list.and.returnValue(
      of({
        content: [],
        page: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0,
      })
    );

    // Filter by CANCELLED
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button');
    const cancelledBtn = Array.from(buttons).find(
      (b: HTMLButtonElement) => b.textContent?.trim() === 'Cancelled'
    );
    expect(cancelledBtn).toBeTruthy();
    cancelledBtn?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No delivery records found');
  });

  it('should filter parcels by status when filter buttons are clicked', () => {
    parcelApiSpy.list.calls.reset();
    parcelApiSpy.list.and.returnValue(of(mockPageResponse));

    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button');
    const inTransitBtn = Array.from(buttons).find(
      (b: HTMLButtonElement) => b.textContent?.trim() === 'In Transit'
    );
    expect(inTransitBtn).toBeTruthy();
    inTransitBtn?.click();
    fixture.detectChanges();

    expect(parcelApiSpy.list).toHaveBeenCalledWith(
      jasmine.objectContaining({
        status: 'IN_TRANSIT',
        page: 0,
      })
    );
  });

  it('should trigger search query when search submitted', () => {
    parcelApiSpy.list.calls.reset();
    parcelApiSpy.list.and.returnValue(of(mockPageResponse));

    const searchInput = fixture.nativeElement.querySelector('#history-search') as HTMLInputElement;
    searchInput.value = 'SD260001001';
    searchInput.dispatchEvent(new Event('input'));

    const searchForm = fixture.nativeElement.querySelector('form');
    searchForm.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(parcelApiSpy.list).toHaveBeenCalledWith(
      jasmine.objectContaining({
        search: 'SD260001001',
        page: 0,
      })
    );
  });

  it('should handle pagination next and previous clicks', () => {
    parcelApiSpy.list.and.returnValue(
      of({
        content: mockParcels,
        page: 0,
        size: 10,
        totalElements: 20,
        totalPages: 2,
      })
    );

    const searchForm = fixture.nativeElement.querySelector('form');
    searchForm.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    parcelApiSpy.list.calls.reset();
    parcelApiSpy.list.and.returnValue(
      of({
        content: mockParcels,
        page: 1,
        size: 10,
        totalElements: 20,
        totalPages: 2,
      })
    );

    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button');
    const nextBtn = Array.from(buttons).find(
      (b: HTMLButtonElement) => b.textContent?.includes('Next')
    );
    expect(nextBtn).toBeTruthy();
    expect(nextBtn?.disabled).toBeFalse();
    nextBtn?.click();
    fixture.detectChanges();

    expect(parcelApiSpy.list).toHaveBeenCalledWith(
      jasmine.objectContaining({
        page: 1,
      })
    );
  });

  it('should show unauthorized banner when 401/403 returned', () => {
    parcelApiSpy.list.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }))
    );

    const searchForm = fixture.nativeElement.querySelector('form');
    searchForm.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Authentication Required');
    expect(fixture.nativeElement.querySelector('a[routerLink="/login"]')).toBeTruthy();
  });

  it('should show error banner on server failure and allow retry', () => {
    parcelApiSpy.list.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Database failure' } }))
    );

    const searchForm = fixture.nativeElement.querySelector('form');
    searchForm.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Database failure');

    // Retry
    parcelApiSpy.list.calls.reset();
    parcelApiSpy.list.and.returnValue(of(mockPageResponse));

    const retryBtn = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button')
    ).find((b: HTMLButtonElement) => b.textContent?.trim() === 'Retry');
    expect(retryBtn).toBeTruthy();
    retryBtn?.click();
    fixture.detectChanges();

    expect(parcelApiSpy.list).toHaveBeenCalled();
  });
});
