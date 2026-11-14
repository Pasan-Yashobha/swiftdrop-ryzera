package com.swiftdrop.reporting.client;

import java.util.UUID;

/**
 * Client for looking up names of couriers, zones, and merchants.
 * Cached for 10 minutes in real implementation. Stubbed for now (returns ID string).
 */
public interface NameLookupClient {
    String getCourierName(UUID courierId);
    String getZoneName(UUID zoneId);
    String getMerchantName(UUID merchantId);
}
