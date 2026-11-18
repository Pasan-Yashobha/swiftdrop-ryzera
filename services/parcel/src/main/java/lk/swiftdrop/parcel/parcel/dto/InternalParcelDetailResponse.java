package lk.swiftdrop.parcel.parcel.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalParcelDetailResponse(
        UUID parcelId,
        String trackingNo,
        String customerId,
        PriceBreakdownDto pricing,
        String currency) {

    public record PriceBreakdownDto(
            BigDecimal baseAmount,
            BigDecimal weightAmount,
            BigDecimal sizeAmount,
            BigDecimal expressAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount) {
    }
}
