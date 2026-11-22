import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { DispatchApi } from '../../core/api/dispatch.api';
import { CourierWorkload, UnassignedParcel } from '../../core/models/dispatch.model';
import { DispatchConsolePage } from './dispatch-console.page';

describe('DispatchConsolePage', () => {
  let component: DispatchConsolePage;
  let fixture: ComponentFixture<DispatchConsolePage>;
  let dispatchApi: jasmine.SpyObj<DispatchApi>;

  const mockParcels: UnassignedParcel[] = [
    {
      parcelId: '11111111-1111-1111-1111-111111111111',
      trackingNumber: 'SWD-TEST-1',
      pickupArea: 'Colombo Central',
      size: 'SMALL',
      weight: 1.5,
      createdAt: new Date().toISOString(),
    },
  ];

  const mockCouriers: CourierWorkload[] = [
    {
      courierId: 'c1111111-1111-1111-1111-111111111111',
      activeDeliveries: 2,
      maxActive: 8,
      available: true,
    },
    {
      courierId: 'c2222222-2222-2222-2222-222222222222',
      activeDeliveries: 0,
      maxActive: 8,
      available: false, // offline
    },
  ];

  beforeEach(async () => {
    const apiSpy = jasmine.createSpyObj('DispatchApi', [
      'getUnassignedParcels',
      'getCourierWorkloads',
      'getAssignments',
      'assignManual',
      'autoAssign',
      'reassign',
      'createStream',
    ]);

    apiSpy.getUnassignedParcels.and.returnValue(of(mockParcels));
    apiSpy.getCourierWorkloads.and.returnValue(of(mockCouriers));
    apiSpy.getAssignments.and.returnValue(of([]));
    apiSpy.createStream.and.returnValue({
      addEventListener: jasmine.createSpy('addEventListener'),
      close: jasmine.createSpy('close'),
    } as unknown as EventSource);

    await TestBed.configureTestingModule({
      imports: [DispatchConsolePage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DispatchApi, useValue: apiSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DispatchConsolePage);
    component = fixture.componentInstance;
    dispatchApi = TestBed.inject(DispatchApi) as jasmine.SpyObj<DispatchApi>;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should load unassigned parcels and courier workloads on init', () => {
    expect(dispatchApi.getUnassignedParcels).toHaveBeenCalled();
    expect(dispatchApi.getCourierWorkloads).toHaveBeenCalled();
    expect(component.unassignedParcels().length).toBe(1);
    expect(component.courierWorkloads().length).toBe(2);
  });

  it('should select parcel when clicked', () => {
    component.toggleParcelSelect(mockParcels[0]);
    expect(component.selectedParcel()?.parcelId).toBe(mockParcels[0].parcelId);

    // Toggle off
    component.toggleParcelSelect(mockParcels[0]);
    expect(component.selectedParcel()).toBeNull();
  });

  it('should show error when clicking an offline courier', () => {
    const offlineCourier = mockCouriers[1];
    component.onCourierCardClick(offlineCourier);
    expect(component.errorMessage()).toContain('offline');
  });

  it('should calculate load percentage and color class correctly', () => {
    expect(component.getLoadPercentage(2, 8)).toBe(25);
    expect(component.getLoadColorClass(2, 8)).toBe('workload-bar--ok');
    expect(component.getLoadColorClass(6, 8)).toBe('workload-bar--warning');
    expect(component.getLoadColorClass(8, 8)).toBe('workload-bar--full');
  });
});
