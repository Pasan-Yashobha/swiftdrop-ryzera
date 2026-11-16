import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ParcelApi } from '../../core/api/parcel.api';
import { PricingApi } from '../../core/api/pricing.api';
import { CustomerParcelDetail } from '../../core/models/parcel.model';
import { PricingEstimateResponse, PricingZone } from '../../core/models/pricing.model';
import { BookDeliveryPage } from './book-delivery.page';

describe('BookDeliveryPage', () => {
  let component: BookDeliveryPage;
  let fixture: ComponentFixture<BookDeliveryPage>;
  let pricingApiSpy: jasmine.SpyObj<PricingApi>;
  let parcelApiSpy: jasmine.SpyObj<ParcelApi>;

  const mockZones: PricingZone[] = [
    { id: 'z1', code: 'COLOMBO', name: 'Colombo' },
    { id: 'z2', code: 'KANDY', name: 'Kandy' },
  ];

  const mockQuote: PricingEstimateResponse = {
    quoteId: 'e2b34a91-4cf1-4560-8f92-5dbce299b830',
    rateCardId: 'rc-101',
    originZone: 'COLOMBO',
    destinationZone: 'KANDY',
    strategyUsed: 'STANDARD',
    currency: 'LKR',
    amounts: {
      baseAmount: 300,
      weightAmount: 200,
      sizeAmount: 50,
      expressAmount: 0,
      discountAmount: 55,
      totalAmount: 495,
    },
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
    promoCode: 'SAVE10',
  };

  const mockCreatedParcel: CustomerParcelDetail = {
    id: 'b6f2b48e-28b3-4613-8ad8-3f5f3e9b1101',
    trackingNumber: 'SD260001001',
    status: 'CREATED',
    recipient: { name: 'Kasun Jayawardena', phone: '+94771234567', email: 'kasun@example.com' },
    pickup: { addressLine: '12 Galle Rd', city: 'Colombo 03', postalCode: '00300' },
    dropoff: { addressLine: '45 Temple St', city: 'Kandy', postalCode: '20000' },
    weightKg: 2.5,
    size: 'MEDIUM',
    deliveryType: 'STANDARD',
    description: 'Electronics',
    notes: 'Handle with care',
    pricing: {
      quoteId: mockQuote.quoteId,
      baseAmount: 300,
      weightAmount: 200,
      sizeAmount: 50,
      expressAmount: 0,
      discountAmount: 55,
      totalFee: 495,
      currency: 'LKR',
    },
    courierId: null,
    failureReason: null,
    deliveredAt: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    history: [],
  };

  beforeEach(async () => {
    pricingApiSpy = jasmine.createSpyObj('PricingApi', ['getZones', 'estimate']);
    parcelApiSpy = jasmine.createSpyObj('ParcelApi', ['create']);

    pricingApiSpy.getZones.and.returnValue(of(mockZones));

    await TestBed.configureTestingModule({
      imports: [BookDeliveryPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PricingApi, useValue: pricingApiSpy },
        { provide: ParcelApi, useValue: parcelApiSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookDeliveryPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads dynamic zones on initialization', () => {
    expect(pricingApiSpy.getZones).toHaveBeenCalled();
    expect(component['zones']().length).toBe(2);
    expect(component['originZone']()).toBe('COLOMBO');
    expect(component['destinationZone']()).toBe('KANDY');
  });

  it('validates booking required fields correctly', () => {
    expect(component['canConfirmBooking']()).toBeFalse();

    // Populate required fields
    component['recipientName'].set('Kasun Jayawardena');
    component['recipientPhone'].set('+94771234567');
    component['pickupAddress'].set('12 Galle Rd');
    component['pickupCity'].set('Colombo 03');
    component['dropoffAddress'].set('45 Temple St');
    component['dropoffCity'].set('Kandy');
    component['weightKg'].set(2.5);

    expect(component['isRecipientValid']()).toBeTrue();
    expect(component['isPickupValid']()).toBeTrue();
    expect(component['isDropoffValid']()).toBeTrue();
    expect(component['isWeightValid']()).toBeTrue();
    expect(component['canRequestEstimate']()).toBeTrue();

    // Still false because no quote has been accepted yet
    expect(component['canConfirmBooking']()).toBeFalse();
  });

  it('requests price estimate and updates currentQuote', () => {
    pricingApiSpy.estimate.and.returnValue(of(mockQuote));

    component['originZone'].set('COLOMBO');
    component['destinationZone'].set('KANDY');
    component['weightKg'].set(2.5);
    component['size'].set('MEDIUM');
    component['deliveryType'].set('STANDARD');
    component['promoCode'].set('SAVE10');

    component['onRequestEstimate']();

    expect(pricingApiSpy.estimate).toHaveBeenCalledWith({
      originZone: 'COLOMBO',
      destinationZone: 'KANDY',
      weightKg: 2.5,
      sizeClass: 'MEDIUM',
      deliveryType: 'STANDARD',
      promoCode: 'SAVE10',
    });
    expect(component['currentQuote']()).toEqual(mockQuote);
    expect(component['isQuoteExpired']()).toBeFalse();
  });

  it('invalidates existing quote when any pricing-relevant field changes', () => {
    component['currentQuote'].set(mockQuote);
    expect(component['currentQuote']()).not.toBeNull();

    // Change weight
    component['onWeightChange']('5.0');
    expect(component['currentQuote']()).toBeNull();

    // Restore quote and change delivery type
    component['currentQuote'].set(mockQuote);
    component['onDeliveryTypeChange']('EXPRESS');
    expect(component['currentQuote']()).toBeNull();

    // Restore quote and change origin zone
    component['currentQuote'].set(mockQuote);
    component['onOriginZoneChange']('GALLE');
    expect(component['currentQuote']()).toBeNull();
  });

  it('prevents confirmation when quote is expired', () => {
    const expiredQuote: PricingEstimateResponse = {
      ...mockQuote,
      expiresAt: new Date(Date.now() - 60000).toISOString(), // 1 minute in past
    };
    component['currentQuote'].set(expiredQuote);
    expect(component['isQuoteExpired']()).toBeTrue();
    expect(component['canConfirmBooking']()).toBeFalse();
  });

  it('submits booking with quoteId and displays confirmed parcel details', () => {
    parcelApiSpy.create.and.returnValue(of(mockCreatedParcel));

    component['recipientName'].set('Kasun Jayawardena');
    component['recipientPhone'].set('+94771234567');
    component['recipientEmail'].set('kasun@example.com');
    component['pickupAddress'].set('12 Galle Rd');
    component['pickupCity'].set('Colombo 03');
    component['dropoffAddress'].set('45 Temple St');
    component['dropoffCity'].set('Kandy');
    component['weightKg'].set(2.5);
    component['size'].set('MEDIUM');
    component['deliveryType'].set('STANDARD');
    component['description'].set('Electronics');
    component['notes'].set('Handle with care');
    component['currentQuote'].set(mockQuote);

    expect(component['canConfirmBooking']()).toBeTrue();

    component['onConfirmBooking']();

    expect(parcelApiSpy.create).toHaveBeenCalledWith({
      recipient: {
        name: 'Kasun Jayawardena',
        phone: '+94771234567',
        email: 'kasun@example.com',
      },
      pickup: {
        addressLine: '12 Galle Rd',
        city: 'Colombo 03',
        postalCode: null,
      },
      dropoff: {
        addressLine: '45 Temple St',
        city: 'Kandy',
        postalCode: null,
      },
      weightKg: 2.5,
      size: 'MEDIUM',
      deliveryType: 'STANDARD',
      description: 'Electronics',
      notes: 'Handle with care',
      quoteId: mockQuote.quoteId,
    });

    expect(component['bookedParcel']()).toEqual(mockCreatedParcel);
    // Duplicate submission prevention: quote is cleared
    expect(component['currentQuote']()).toBeNull();
  });

  it('handles 409/410 quote conflicts by clearing quote and showing recalculation message', () => {
    const conflictError = { status: 409, error: { message: 'Quote already accepted' } };
    parcelApiSpy.create.and.returnValue(throwError(() => conflictError));

    component['recipientName'].set('Kasun Jayawardena');
    component['recipientPhone'].set('+94771234567');
    component['pickupAddress'].set('12 Galle Rd');
    component['pickupCity'].set('Colombo 03');
    component['dropoffAddress'].set('45 Temple St');
    component['dropoffCity'].set('Kandy');
    component['currentQuote'].set(mockQuote);

    component['onConfirmBooking']();

    expect(component['currentQuote']()).toBeNull();
    expect(component['bookingError']()).toContain('expired or has already been accepted');
    expect(component['submitting']()).toBeFalse();
  });
});
