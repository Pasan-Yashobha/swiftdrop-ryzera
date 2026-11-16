
/**
 * Receipt metadata returned by the Document Service.
 *
 * GET /api/v1/receipts/{parcelId}
 */
export interface ReceiptMetadata {
    id: string;
    receiptNo: string;
    parcelId: string;
    trackingNumber: string;
    deliveredAt: string;
    totalAmount: number;
    currency: string;
    generatedAt: string;
}