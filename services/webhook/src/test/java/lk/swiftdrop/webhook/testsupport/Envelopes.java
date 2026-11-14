package lk.swiftdrop.webhook.testsupport;

import java.util.UUID;

/** Event envelopes as other services publish them to {@code swiftdrop.events}. */
public final class Envelopes {

    private Envelopes() {
    }

    public static String parcelEvent(String eventId, String eventType, UUID merchantId) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-11-13T08:30:00Z\",\"producer\":\"parcel-service\","
                + "\"correlationId\":\"corr-" + eventId + "\",\"aggregateId\":\"P-100\","
                + "\"data\":{\"merchantId\":\"" + merchantId + "\",\"parcelId\":\"P-100\",\"trackingNo\":\"SD100\"}}";
    }

    public static String parcelDelivered(String eventId, UUID merchantId) {
        return parcelEvent(eventId, "parcel.status.delivered", merchantId);
    }
}
