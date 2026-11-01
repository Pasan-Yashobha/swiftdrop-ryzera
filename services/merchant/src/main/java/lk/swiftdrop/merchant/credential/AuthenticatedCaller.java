package lk.swiftdrop.merchant.credential;

import lk.swiftdrop.merchant.security.MerchantApiPrincipal;

/**
 * A merchant API caller whose credential and merchant were just verified.
 *
 * @param rateLimitPerMinute the merchant's current request limit
 */
public record AuthenticatedCaller(MerchantApiPrincipal principal, int rateLimitPerMinute) {
}
