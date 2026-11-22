package lk.swiftdrop.parcel.parcel.dto;

public record InternalParcelBookingResponse(
        String parcelId,
        String trackingNo,
        String status,
        MoneyDto fee) {
}
