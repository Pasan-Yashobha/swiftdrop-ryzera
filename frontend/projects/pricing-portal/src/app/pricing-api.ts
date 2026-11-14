import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface Zone {
  id: string;
  code: string;
  name: string;
  description?: string;
  active?: boolean;
}
export interface ZoneInput {
  code: string;
  name: string;
  description: string;
}
export interface RateInput {
  originZoneId: string;
  destinationZoneId: string;
  baseFee: number;
  perKgFee: number;
  smallSurcharge: number;
  mediumSurcharge: number;
  largeSurcharge: number;
  expressMultiplier: number | null;
  validFrom: string;
  validTo: string | null;
}
export interface Rate extends RateInput {
  id: string;
  active: boolean;
}
export interface EstimateInput {
  promoCode?: string;
  originZone: string;
  destinationZone: string;
  weightKg: number;
  sizeClass: string;
  deliveryType: 'STANDARD' | 'EXPRESS';
}
export interface Quote {
  promoCode: string | null;
  quoteId: string;
  currency: string;
  expiresAt: string;
  amounts: {
    baseAmount: number;
    weightAmount: number;
    sizeAmount: number;
    expressAmount: number;
    discountAmount: number;
    totalAmount: number;
  };
}
export interface PromoInput {
  code: string;
  discountType: 'PERCENT' | 'FLAT';
  discountValue: number;
  minOrderValue: number;
  maxUses: number | null;
  validFrom: string;
  validTo: string | null;
}
export interface Promo extends PromoInput {
  id: string;
  usedCount: number;
  active: boolean;
}
/** Receipt metadata returned by the Document Service (port 8092). */
export interface Receipt {
  id: string;
  receiptNo: string;
  parcelId: string;
  trackingNumber: string;
  deliveredAt: string;
  totalAmount: number;
  currency: string;
  generatedAt: string;
}
/** Confirmed delivery snapshot accepted by the dev-only receipt endpoint. */
export interface ReceiptSnapshot {
  parcelId: string;
  customerId: string;
  trackingNumber: string;
  deliveredAt: string;
  baseAmount: number;
  weightAmount: number;
  sizeAmount: number;
  expressAmount: number;
  discountAmount: number;
  totalAmount: number;
}
/** Invoice summary returned by the Document Service (port 8092). */
export interface Invoice {
  id: string;
  invoiceNo: string;
  merchantId: string;
  merchantName: string;
  periodStart: string;
  periodEnd: string;
  totalAmount: number;
  currency: string;
  lineCount: number;
  generatedAt: string;
}
export interface InvoiceLine {
  lineNo: number;
  parcelId: string;
  trackingNumber: string;
  description: string;
  amount: number;
}
export interface InvoiceDetail {
  invoice: Invoice;
  lines: InvoiceLine[];
}
/** Billing lines sent by Admin. There is no total: the server adds the lines up. */
export interface InvoiceInput {
  merchantId: string;
  merchantName: string;
  periodStart: string;
  periodEnd: string;
  lines: { parcelId: string; trackingNumber: string; description: string; amount: number }[];
}
interface Page<T> {
  items: T[];
  totalElements: number;
}
/** One MCP tool the assistant used to answer (mirrors ChatReply.ToolTrace in the Assistant service). */
export interface ToolTrace {
  tool: string;
  arguments: Record<string, unknown>;
  failed: boolean;
}
/** Response of POST /api/v1/assistant/chat (mirrors the ChatReply record). */
export interface ChatReply {
  conversationId: string;
  reply: string;
  toolCalls: ToolTrace[];
}

export function errorMessage(error: unknown, service = 'Pricing is running on port 8085'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401) return 'Your session has expired. Sign out and connect again.';
    if (error.status === 403) return 'This action requires a different account role.';
    if (typeof error.error?.detail === 'string') return error.error.detail;
    if (error.status === 400) return 'Check the form values and try again.';
    if (error.status === 404) return 'That record was not found for this account.';
    if (error.status === 409) return 'A different receipt already exists for this parcel.';
  }
  return `Could not complete the request. Check that ${service}.`;
}

@Injectable({ providedIn: 'root' })
export class PricingApi {
  private readonly http = inject(HttpClient);
  readonly token = signal('');
  readonly role = signal('CUSTOMER');

  private options() {
    return { headers: new HttpHeaders({ Authorization: `Bearer ${this.token()}` }) };
  }

  async connectDevelopment(role: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<{ accessToken: string }>('/api/v1/dev/tokens', {
        sub: `pricing-demo-${role.toLowerCase()}`,
        role,
      }),
    );
    this.role.set(role);
    this.token.set(response.accessToken);
  }

  async all<T>(path: string): Promise<T[]> {
    const items: T[] = [];
    let page = 0;
    let total: number;
    do {
      const response = await firstValueFrom(
        this.http.get<Page<T>>(`${path}?page=${page}&size=100`, this.options()),
      );
      items.push(...response.items);
      total = response.totalElements;
      page++;
      if (response.items.length === 0) break;
    } while (items.length < total);
    return items;
  }

  save<T>(path: string, body: unknown, id = ''): Promise<T> {
    return firstValueFrom(
      id
        ? this.http.put<T>(`${path}/${id}`, body, this.options())
        : this.http.post<T>(path, body, this.options()),
    );
  }

  deactivate(path: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${path}/${id}`, this.options()));
  }

  /**
   * Fetches the PDF bytes with the bearer token attached. A plain link would not
   * send the in-memory token, so the Document Service would answer 401.
   */
  downloadReceipt(id: string): Promise<Blob> {
    return this.downloadPdf(`/api/v1/receipts/${id}/download`);
  }

  downloadInvoice(id: string): Promise<Blob> {
    return this.downloadPdf(`/api/v1/invoices/${id}/download`);
  }

  getInvoice(id: string): Promise<InvoiceDetail> {
    return firstValueFrom(this.http.get<InvoiceDetail>(`/api/v1/invoices/${id}`, this.options()));
  }

  /** Sends one chat message to the Assistant Service (port 8093) as the signed-in user. */
  chat(message: string, conversationId: string | null): Promise<ChatReply> {
    return firstValueFrom(
      this.http.post<ChatReply>(
        '/api/v1/assistant/chat',
        conversationId ? { message, conversationId } : { message },
        this.options(),
      ),
    );
  }

  /** "New chat": asks the server to forget the conversation's memory. */
  forgetConversation(conversationId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(
        `/api/v1/assistant/conversations/${encodeURIComponent(conversationId)}`,
        this.options(),
      ),
    );
  }

  private downloadPdf(url: string): Promise<Blob> {
    return firstValueFrom(this.http.get(url, { ...this.options(), responseType: 'blob' }));
  }
}
