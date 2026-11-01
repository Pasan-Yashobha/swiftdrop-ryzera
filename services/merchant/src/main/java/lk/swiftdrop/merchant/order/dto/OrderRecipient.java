package lk.swiftdrop.merchant.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.Phone;
import lk.swiftdrop.merchant.common.validation.PlainText;

/** Who receives the parcel. */
public record OrderRecipient(
        @NotBlank @Size(max = 120) @PlainText String name,
        @NotBlank @Phone String phone) {

    public OrderRecipient {
        name = Inputs.trim(name);
        phone = Inputs.normalisePhone(phone);
    }

    /** Name and phone number are personal data, and Spring logs request and response bodies at DEBUG. */
    @Override
    public String toString() {
        return "OrderRecipient[redacted]";
    }
}
