package lk.swiftdrop.merchant.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.PlainText;

/** A pickup or drop-off address. */
public record OrderAddress(
        @NotBlank @Size(max = 300) @PlainText String addressLine,
        @NotBlank @Size(max = 100) @PlainText String city,
        @Size(max = 16) @PlainText String postalCode) {

    public OrderAddress {
        addressLine = Inputs.trim(addressLine);
        city = Inputs.trim(city);
        postalCode = Inputs.trimToNull(postalCode);
    }

    /** Addresses are personal data, and Spring logs request and response bodies at DEBUG. */
    @Override
    public String toString() {
        return "OrderAddress[redacted]";
    }
}
