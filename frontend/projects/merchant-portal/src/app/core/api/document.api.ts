import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  Invoice,
  InvoiceDetail,
  InvoiceRequest,
  Receipt,
  ReceiptSnapshot,
} from '../models/document.model';
import { ItemPage } from '../models/pricing.model';

/**
 * Document service (Member 5). Customers see only their own receipts and merchants only their own
 * invoices; Admin sees all. The server enforces this from the token, so the same calls serve every role.
 */
@Injectable({ providedIn: 'root' })
export class DocumentApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);

  receipts(page = 0, size = 20): Observable<ItemPage<Receipt>> {
    return this.http.get<ItemPage<Receipt>>(`${this.api}/receipts`, { params: paging(page, size) });
  }

  /**
   * The PDF bytes, fetched through HttpClient so the interceptor attaches the bearer token.
   * A plain link would not carry the token and would get 401.
   */
  receiptPdf(receiptId: string): Observable<Blob> {
    return this.http.get(`${this.api}/receipts/${encodeURIComponent(receiptId)}/download`, {
      responseType: 'blob',
    });
  }

  /** Development only (Admin, dev profile): stands in for the parcel.status.delivered event. */
  createTestReceipt(snapshot: ReceiptSnapshot): Observable<Receipt> {
    return this.http.post<Receipt>(`${this.api}/dev/receipts`, snapshot);
  }

  invoices(page = 0, size = 20): Observable<ItemPage<Invoice>> {
    return this.http.get<ItemPage<Invoice>>(`${this.api}/invoices`, { params: paging(page, size) });
  }

  invoice(invoiceId: string): Observable<InvoiceDetail> {
    return this.http.get<InvoiceDetail>(`${this.api}/invoices/${encodeURIComponent(invoiceId)}`);
  }

  invoicePdf(invoiceId: string): Observable<Blob> {
    return this.http.get(`${this.api}/invoices/${encodeURIComponent(invoiceId)}/download`, {
      responseType: 'blob',
    });
  }

  /** Admin. The same merchant, period and lines return the existing invoice instead of a second one. */
  generateInvoice(request: InvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.api}/invoices/generate`, request);
  }
}

function paging(page: number, size: number): HttpParams {
  return new HttpParams().set('page', page).set('size', size);
}
