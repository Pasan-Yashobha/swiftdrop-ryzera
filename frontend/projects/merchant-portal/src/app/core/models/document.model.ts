/** Document service (Member 5): delivery receipts for customers and invoices for merchants. */

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

/** Confirmed delivery snapshot accepted by the development-only receipt endpoint (Admin). */
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

export interface Invoice {
  id: string;
  invoiceNo: string;
  /** The merchant owner's Identity user id, which is what ownership is checked against. */
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
export interface InvoiceRequest {
  merchantId: string;
  merchantName: string;
  periodStart: string;
  periodEnd: string;
  lines: { parcelId: string; trackingNumber: string; description: string; amount: number }[];
}
