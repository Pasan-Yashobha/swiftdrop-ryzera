package lk.swiftdrop.merchant.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.PlainText;

/**
 * Body of the order creation endpoints of the portal and the merchant API (CONTRACT section 5.4). Text is trimmed
 * and blank optional fields become null before validation runs.
 */
public record CreateOrderRequest(
        @Size(min = 1, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._:/#-]+$", message = "may only contain letters, digits and . _ : / # -")
        String externalRef,
        @NotNull @Valid OrderRecipient recipient,
        @NotNull @Valid OrderAddress pickup,
        @NotNull @Valid OrderAddress dropoff,
        @NotNull @Valid OrderParcel parcel,
        @Size(max = 500) @PlainText String notes) {

    public CreateOrderRequest {
        externalRef = Inputs.trimToNull(externalRef);
        notes = Inputs.trimToNull(notes);
    }

    /** The notes are free text, and Spring logs request bodies at DEBUG. */
    @Override
    public String toString() {
        return "CreateOrderRequest[externalRef=" + externalRef + ", recipient=" + recipient + ", pickup=" + pickup
                + ", dropoff=" + dropoff + ", parcel=" + parcel + "]";
    }
}
