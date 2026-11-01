import { OrderSource, ParcelSize } from '../../../core/models/order.model';

export const PARCEL_SIZE_LABELS: Readonly<Record<ParcelSize, string>> = {
  SMALL: 'Small',
  MEDIUM: 'Medium',
  LARGE: 'Large',
};

export const ORDER_SOURCE_LABELS: Readonly<Record<OrderSource, string>> = {
  API: 'API',
  PORTAL: 'Merchant portal',
  BULK: 'Bulk upload',
};
