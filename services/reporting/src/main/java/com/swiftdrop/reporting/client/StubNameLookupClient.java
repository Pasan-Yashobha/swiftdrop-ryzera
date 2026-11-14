package com.swiftdrop.reporting.client;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub implementation returning IDs as strings if service is not connected (brief §5).
 */
@Component
public class StubNameLookupClient implements NameLookupClient {

    @Override
    public String getCourierName(UUID courierId) {
        return courierId != null ? "Courier-" + courierId.toString().substring(0, 8) : "Unknown";
    }

    @Override
    public String getZoneName(UUID zoneId) {
        return zoneId != null ? "Zone-" + zoneId.toString().substring(0, 8) : "Unknown";
    }

    @Override
    public String getMerchantName(UUID merchantId) {
        return merchantId != null ? "Merchant-" + merchantId.toString().substring(0, 8) : "Unknown";
    }
}
