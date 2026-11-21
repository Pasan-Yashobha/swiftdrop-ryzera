package lk.swiftdrop.parcel.pricing;

import java.util.UUID;

public interface PricingClient {

    /**
     * Accepts a pricing quote via Member 05's Pricing Service contract (POST
     * /internal/quotes/{quoteId}/accept).
     *
     * @param quoteId  Quote UUID from Pricing Service
     * @param parcelId Parcel UUID associated with the accepted quote
     * @return Accepted pricing breakdown snapshot
     * @throws lk.swiftdrop.parcel.common.UnprocessableException      if quote is
     *                                                                expired,
     *                                                                invalid or
     *                                                                rejected
     * @throws lk.swiftdrop.parcel.common.ServiceUnavailableException if Pricing
     *                                                                Service is
     *                                                                down
     */
    PricingSnapshot acceptQuote(UUID quoteId, UUID parcelId);
}