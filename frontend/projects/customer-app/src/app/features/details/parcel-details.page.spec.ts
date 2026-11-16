import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { ParcelApi } from '../../core/api/parcel.api';
import { ReceiptApi } from '../../core/api/receipt.api';
import { CustomerParcelDetail } from '../../core/models/parcel.model';
import { ReceiptMetadata } from '../../core/models/receipt.model';
import { ParcelDetailsPage } from './parcel-details.page';

describe('ParcelDetailsPage Receipt Integration', () => {
  let component: ParcelDetailsPage;
  let fixture: ComponentFixture<ParcelDetailsPage>;
  let parcelApiSpy: jasmine.SpyObj<ParcelApi>;
  let receiptApiSpy: jasmine.SpyObj<ReceiptApi>;
  let paramMapSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const mockDeliveredParcel: CustomerParcelDetail = {
    id: 'parcel-uuid-123',
    trackingNumber: 'SD260001001',
    status: 'DELIVERED',
    recipient: { name: 'Kasun Jayawardena', phone: '+94771234567', email: 'kasun@example.com' },
    pickup: { addressLine: '12 Galle Rd', city: 'Colombo 03', postalCode: '00300' },
    dropoff: { addressLine: '45 Temple St', city: 'Kandy', postalCode: '20000' },
    weightKg: 2.5,
    size: 'MEDIUM',
    deliveryType: 'STANDARD',
    description: 'Books',
    notes: 'Fragile',
    pricing: {
      quoteId: 'quote-uuid-1',
      baseAmount: 300,
      weightAmount: 200,
      sizeAmount: 50,
      expressAmount: 0,
      discountAmount: 0,
      totalFee: 550,
      currency: 'LKR',
    },
    courierId: 'courier-uuid-1',
    failureReason: null,
    deliveredAt: '2026-11-15T14:30:00Z',
    createdAt: '2026-11-15T08:00:00Z',
    updatedAt: '2026-11-15T14:30:00Z',
    history: [],
  };

  const mockInTransitParcel: CustomerParcelDetail = {
    ...mockDeliveredParcel,
    id: 'parcel-uuid-456',
    trackingNumber: 'SD260001002',
    status: 'IN_TRANSIT',
    deliveredAt: null,
  };

  const mockReceiptMetadata: ReceiptMetadata = {
    id: 'receipt-uuid-999',
    receiptNo: 'REC-20261115-001',
    parcelId: 'parcel-uuid-123',
    trackingNumber: 'SD260001001',
    deliveredAt: '2026-11-15T14:30:00Z',
    totalAmount: 550,
    currency: 'LKR',
    generatedAt: '2026-11-15T14:30:05Z',
  };

  const mockBlob = new Blob(['%PDF-1.4 test receipt content'], { type: 'application/pdf' });

  beforeEach(async () => {
    parcelApiSpy = jasmine.createSpyObj<ParcelApi>('ParcelApi', ['get']);
    receiptApiSpy = jasmine.createSpyObj<ReceiptApi>('ReceiptApi', ['getByParcel', 'download']);
    paramMapSubject = new BehaviorSubject(convertToParamMap({ id: 'parcel-uuid-123' }));

    parcelApiSpy.get.and.returnValue(of(mockDeliveredParcel));
    receiptApiSpy.getByParcel.and.returnValue(of(mockReceiptMetadata));
    receiptApiSpy.download.and.returnValue(of(mockBlob));

    await TestBed.configureTestingModule({
      imports: [ParcelDetailsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ParcelApi, useValue: parcelApiSpy },
        { provide: ReceiptApi, useValue: receiptApiSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramMapSubject.asObservable(),
            snapshot: { paramMap: convertToParamMap({ id: 'parcel-uuid-123' }) },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ParcelDetailsPage);
    component = fixture.componentInstance;
  });

  describe('Download button visibility', () => {
    it('displays the Download Receipt button when the parcel status is DELIVERED', () => {
      parcelApiSpy.get.and.returnValue(of(mockDeliveredParcel));
      fixture.detectChanges();

      const downloadBtn = fixture.debugElement.query(By.css('[data-testid="download-receipt-btn"]'));
      expect(downloadBtn).toBeTruthy();
      expect(downloadBtn.nativeElement.textContent).toContain('Download Receipt');
    });

    it('does not display the Download Receipt button when parcel status is not DELIVERED (e.g. IN_TRANSIT)', () => {
      parcelApiSpy.get.and.returnValue(of(mockInTransitParcel));
      paramMapSubject.next(convertToParamMap({ id: 'parcel-uuid-456' }));
      fixture.detectChanges();

      const downloadBtn = fixture.debugElement.query(By.css('[data-testid="download-receipt-btn"]'));
      expect(downloadBtn).toBeNull();
    });
  });

  describe('Successful receipt lookup and download flow', () => {
    it('calls getByParcel and download, passing the receipt ID and triggering file download with asynchronous cleanup', () => {
      jasmine.clock().install();
      try {
        fixture.detectChanges();

        const createUrlSpy = spyOn(window.URL, 'createObjectURL').and.returnValue('blob:http://localhost/mock-blob-url');
        const revokeUrlSpy = spyOn(window.URL, 'revokeObjectURL');
        const triggerSpy = spyOn(component, 'triggerBlobDownload').and.callThrough();
        const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');

        const downloadBtn = fixture.debugElement.query(By.css('[data-testid="download-receipt-btn"]'));
        downloadBtn.nativeElement.click();
        fixture.detectChanges();

        // Verified: getByParcel called with parcel ID
        expect(receiptApiSpy.getByParcel).toHaveBeenCalledWith('parcel-uuid-123');

        // Verified: download called with receipt ID (not parcel ID!)
        expect(receiptApiSpy.download).toHaveBeenCalledWith('receipt-uuid-999');

        // Verified: triggerBlobDownload invoked with received Blob and receiptNo
        expect(triggerSpy).toHaveBeenCalledWith(mockBlob, 'REC-20261115-001');

        // Verified: Object URL created and clicked
        expect(createUrlSpy).toHaveBeenCalledWith(mockBlob);
        expect(clickSpy).toHaveBeenCalled();

        // Verified: Object URL is NOT revoked synchronously immediately after click
        expect(revokeUrlSpy).not.toHaveBeenCalled();

        // Advance clock by 1000ms to verify asynchronous revocation cleanup
        jasmine.clock().tick(1000);
        expect(revokeUrlSpy).toHaveBeenCalledWith('blob:http://localhost/mock-blob-url');

        // State reset after download
        expect(component.downloadingReceipt()).toBeFalse();
        expect(component.receiptError()).toBeNull();
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('uses the receipt ID and NOT the parcel ID when requesting the PDF download', () => {
      fixture.detectChanges();
      spyOn(component, 'triggerBlobDownload');

      component.downloadReceipt();

      expect(receiptApiSpy.getByParcel).toHaveBeenCalledWith('parcel-uuid-123');
      expect(receiptApiSpy.download).toHaveBeenCalledWith('receipt-uuid-999');
      expect(receiptApiSpy.download).not.toHaveBeenCalledWith('parcel-uuid-123');
    });

    it('removes the temporary anchor element from the DOM even if triggering click throws an exception', () => {
      spyOn(window.URL, 'createObjectURL').and.returnValue('blob:mock-url');
      spyOn(window.URL, 'revokeObjectURL');
      let appendedAnchor: HTMLAnchorElement | null = null;
      const originalAppendChild = document.body.appendChild.bind(document.body);
      spyOn(document.body, 'appendChild').and.callFake((node) => {
        if (node instanceof HTMLAnchorElement) {
          appendedAnchor = node;
        }
        return originalAppendChild(node);
      });
      spyOn(HTMLAnchorElement.prototype, 'click').and.throwError(new Error('Click error'));

      expect(() => component.triggerBlobDownload(mockBlob, 'REC-001')).toThrowError('Click error');

      expect(appendedAnchor).not.toBeNull();
      expect(document.body.contains(appendedAnchor)).toBeFalse();
    });
  });

  describe('Duplicate-click prevention', () => {
    it('prevents multiple simultaneous download requests while a download is in progress', () => {
      // Keep request pending
      receiptApiSpy.getByParcel.and.returnValue(new Observable(() => {
        // Pending
      }));

      fixture.detectChanges();

      const downloadBtn = fixture.debugElement.query(By.css('[data-testid="download-receipt-btn"]'));
      downloadBtn.nativeElement.click();
      fixture.detectChanges();

      expect(component.downloadingReceipt()).toBeTrue();
      expect(downloadBtn.nativeElement.disabled).toBeTrue();
      expect(downloadBtn.nativeElement.textContent).toContain('Downloading Receipt...');
      expect(receiptApiSpy.getByParcel).toHaveBeenCalledTimes(1);

      // Second click while in progress
      downloadBtn.nativeElement.click();
      component.downloadReceipt();
      expect(receiptApiSpy.getByParcel).toHaveBeenCalledTimes(1);
    });
  });

  describe('Error handling and retry', () => {
    it('handles HTTP 404 when receipt is not available or generation is pending', () => {
      fixture.detectChanges();
      const notFoundError = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
      receiptApiSpy.getByParcel.and.returnValue(throwError(() => notFoundError));

      component.downloadReceipt();
      fixture.detectChanges();

      expect(component.downloadingReceipt()).toBeFalse();
      expect(component.receiptError()).toContain('Receipt is not available yet');
      expect(component.receiptError()).toContain('pending');

      const errorAlert = fixture.debugElement.query(By.css('[role="alert"]'));
      expect(errorAlert).toBeTruthy();
      expect(errorAlert.nativeElement.textContent).toContain('Receipt is not available yet');

      // Retry button is available
      const retryBtn = fixture.debugElement.query(By.css('[data-testid="retry-receipt-btn"]'));
      expect(retryBtn).toBeTruthy();

      // Retrying calls downloadReceipt again
      receiptApiSpy.getByParcel.and.returnValue(of(mockReceiptMetadata));
      spyOn(component, 'triggerBlobDownload');
      retryBtn.nativeElement.click();
      fixture.detectChanges();

      expect(receiptApiSpy.getByParcel).toHaveBeenCalledTimes(2);
      expect(component.receiptError()).toBeNull();
    });

    it('handles HTTP 401/403 authorization errors', () => {
      fixture.detectChanges();
      const unauthorizedError = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });
      receiptApiSpy.getByParcel.and.returnValue(throwError(() => unauthorizedError));

      component.downloadReceipt();
      fixture.detectChanges();

      expect(component.downloadingReceipt()).toBeFalse();
      expect(component.receiptError()).toContain('Authentication required');
    });

    it('handles HTTP 0 network failures', () => {
      fixture.detectChanges();
      const networkError = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });
      receiptApiSpy.getByParcel.and.returnValue(throwError(() => networkError));

      component.downloadReceipt();
      fixture.detectChanges();

      expect(component.downloadingReceipt()).toBeFalse();
      expect(component.receiptError()).toContain('Network error');
    });
  });

  describe('Navigation and state reset', () => {
    it('resets receipt download state and errors when navigating to another parcel', () => {
      fixture.detectChanges();

      // Trigger an error on current parcel
      const notFoundError = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
      receiptApiSpy.getByParcel.and.returnValue(throwError(() => notFoundError));
      component.downloadReceipt();
      fixture.detectChanges();

      expect(component.receiptError()).not.toBeNull();

      // Navigate to another parcel
      const secondParcel: CustomerParcelDetail = {
        ...mockDeliveredParcel,
        id: 'parcel-uuid-888',
      };
      parcelApiSpy.get.and.returnValue(of(secondParcel));
      paramMapSubject.next(convertToParamMap({ id: 'parcel-uuid-888' }));
      fixture.detectChanges();

      // Verified: State is cleanly reset
      expect(component.receiptError()).toBeNull();
      expect(component.downloadingReceipt()).toBeFalse();
    });
  });

  describe('Safe filename generation', () => {
    it('sanitizes unsafe characters in receipt number for the downloaded file', () => {
      spyOn(window.URL, 'createObjectURL').and.returnValue('blob:mock');
      spyOn(window.URL, 'revokeObjectURL');
      let createdAnchor: HTMLAnchorElement | null = null;
      const originalCreate = document.createElement.bind(document);
      spyOn(document, 'createElement').and.callFake((tagName: string) => {
        const el = originalCreate(tagName);
        if (tagName === 'a') {
          createdAnchor = el as HTMLAnchorElement;
        }
        return el;
      });

      component.triggerBlobDownload(mockBlob, 'REC/2026 11:15#001');

      expect(createdAnchor).not.toBeNull();
      expect(createdAnchor!.download).toBe('receipt-REC_2026_11_15_001.pdf');
    });
  });
});
