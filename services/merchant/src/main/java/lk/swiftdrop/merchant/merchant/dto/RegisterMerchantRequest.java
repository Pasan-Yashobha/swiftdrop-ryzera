package lk.swiftdrop.merchant.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.Phone;
import lk.swiftdrop.merchant.common.validation.PlainText;
import lk.swiftdrop.merchant.merchant.MerchantProfile;

/**
 * Business details sent to {@code POST /merchants/register} and, unchanged, to {@code PUT /merchants/me}.
 * Input is normalised before validation: text is trimmed, the email is lower-cased and spaces, dashes and
 * brackets are removed from the phone number.
 *
 * <p>Enforces clean input hygiene and prevents XSS by forbidding unescaped HTML characters in text fields.
 */
public record RegisterMerchantRequest(
        @NotBlank @Size(min = 2, max = 150) @PlainText String businessName,
        @Size(max = 64) @PlainText String businessRegNo,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @NotBlank @Phone String contactPhone,
        @NotBlank @Size(max = 255) @PlainText String addressLine,
        @NotBlank @Size(max = 100) @PlainText String city,
        @Size(max = 255) @Pattern(regexp = "^https?://\\S+$", message = "must be an http or https URL")
        String website) {

    public RegisterMerchantRequest {
        businessName = Inputs.trim(businessName);
        businessRegNo = Inputs.trimToNull(businessRegNo);
        contactEmail = contactEmail == null ? null : contactEmail.trim().toLowerCase(Locale.ROOT);
        contactPhone = Inputs.normalisePhone(contactPhone);
        addressLine = Inputs.trim(addressLine);
        city = Inputs.trim(city);
        website = Inputs.trimToNull(website);
    }

    public MerchantProfile toProfile() {
        return new MerchantProfile(businessName, businessRegNo, contactEmail, contactPhone, addressLine, city, website);
    }
}
