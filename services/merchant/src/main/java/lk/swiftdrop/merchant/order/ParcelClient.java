package lk.swiftdrop.merchant.order;

/** Books parcels with the Parcel Service (M4) for live orders. */
public interface ParcelClient {

    /**
     * Books a parcel for an order. The call must not run inside a database transaction.
     *
     * @throws lk.swiftdrop.merchant.common.UnprocessableException        {@code PARCEL_REJECTED}: the parcel service
     *                                                                    refused the order, retrying will not help
     * @throws lk.swiftdrop.merchant.common.ServiceUnavailableException   {@code PARCEL_SERVICE_UNAVAILABLE}: no usable
     *                                                                    answer, the order may be retried
     */
    ParcelBooking createParcel(ParcelBookingRequest request);
}
