package lk.swiftdrop.merchant.order;

import lk.swiftdrop.merchant.order.dto.Money;

/**
 * A booked parcel.
 *
 * @param parcelId   id in the parcel service, null for sandbox orders
 * @param trackingNo tracking number printed on the parcel
 * @param fee        delivery fee
 */
public record ParcelBooking(String parcelId, String trackingNo, Money fee) {
}
