package lk.swiftdrop.merchant.merchant;

/** The business details a merchant maintains about itself. */
public record MerchantProfile(
        String businessName,
        String businessRegNo,
        String contactEmail,
        String contactPhone,
        String addressLine,
        String city,
        String website) {
}
