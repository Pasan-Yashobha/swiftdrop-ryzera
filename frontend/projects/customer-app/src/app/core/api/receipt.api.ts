import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { ReceiptMetadata } from '../models/receipt.model';

@Injectable({ providedIn: 'root' })
export class ReceiptApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  private get receiptsUrl(): string {
    return `${this.baseUrl}/receipts`;
  }

  /**
   * Fetch receipt metadata for a delivered parcel.
   * Calls GET /api/v1/receipts/{parcelId}
   */
  getByParcel(parcelId: string): Observable<ReceiptMetadata> {
    return this.http.get<ReceiptMetadata>(
      `${this.receiptsUrl}/${encodeURIComponent(parcelId.trim())}`
    );
  }

  /**
   * Download the PDF receipt document as a raw Blob.
   * Calls GET /api/v1/receipts/{receiptId}/download
   */
  download(receiptId: string): Observable<Blob> {
    return this.http.get(
      `${this.receiptsUrl}/${encodeURIComponent(receiptId.trim())}/download`,
      { responseType: 'blob' }
    );
  }
}
