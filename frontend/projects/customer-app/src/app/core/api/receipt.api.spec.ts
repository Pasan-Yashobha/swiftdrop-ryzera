import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReceiptMetadata } from '../models/receipt.model';
import { ReceiptApi } from './receipt.api';

describe('ReceiptApi', () => {
  let api: ReceiptApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ReceiptApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('calls GET /api/v1/receipts/{parcelId} and returns receipt metadata', () => {
    const parcelId = 'parcel-uuid-123';
    const mockReceipt: ReceiptMetadata = {
      id: 'receipt-uuid-456',
      receiptNo: 'REC-20261115-001',
      parcelId: 'parcel-uuid-123',
      trackingNumber: 'SD260001001',
      deliveredAt: '2026-11-15T10:00:00Z',
      totalAmount: 620,
      currency: 'LKR',
      generatedAt: '2026-11-15T10:00:05Z',
    };

    api.getByParcel(parcelId).subscribe((res) => {
      expect(res).toEqual(mockReceipt);
      expect(res.receiptNo).toBe('REC-20261115-001');
      expect(res.totalAmount).toBe(620);
    });

    const req = http.expectOne(`/api/v1/receipts/${parcelId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockReceipt);
  });

  it('encodes special characters in parcelId when calling getByParcel', () => {
    const parcelId = 'parcel/special id#1';
    api.getByParcel(parcelId).subscribe();

    const expectedUrl = `/api/v1/receipts/${encodeURIComponent('parcel/special id#1')}`;
    const req = http.expectOne(expectedUrl);
    expect(req.request.method).toBe('GET');
    req.flush({} as ReceiptMetadata);
  });

  it('calls GET /api/v1/receipts/{receiptId}/download with responseType blob', () => {
    const receiptId = 'receipt-uuid-456';
    const mockBlob = new Blob(['%PDF-1.4 mock pdf content'], { type: 'application/pdf' });

    api.download(receiptId).subscribe((blob) => {
      expect(blob).toBeTruthy();
      expect(blob.type).toBe('application/pdf');
      expect(blob.size).toBe(mockBlob.size);
    });

    const req = http.expectOne(`/api/v1/receipts/${receiptId}/download`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(mockBlob);
  });

  it('encodes special characters in receiptId when calling download', () => {
    const receiptId = 'receipt/special id#2';
    api.download(receiptId).subscribe();

    const expectedUrl = `/api/v1/receipts/${encodeURIComponent('receipt/special id#2')}/download`;
    const req = http.expectOne(expectedUrl);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });
});
