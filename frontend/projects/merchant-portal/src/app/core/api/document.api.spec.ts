import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DocumentApi } from './document.api';

describe('DocumentApi', () => {
  let api: DocumentApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DocumentApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pages the receipt list', () => {
    api.receipts(1, 10).subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/receipts');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
  });

  it('downloads a receipt PDF as a blob', () => {
    let received: Blob | undefined;
    api.receiptPdf('r-1').subscribe((blob) => (received = blob));

    const request = http.expectOne('/api/v1/receipts/r-1/download');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
    expect(received?.type).toBe('application/pdf');
  });

  it('loads one invoice with its lines and its PDF', () => {
    api.invoice('i-1').subscribe();
    api.invoicePdf('i-1').subscribe();

    expect(http.expectOne('/api/v1/invoices/i-1').request.method).toBe('GET');
    expect(http.expectOne('/api/v1/invoices/i-1/download').request.responseType).toBe('blob');
  });
});
