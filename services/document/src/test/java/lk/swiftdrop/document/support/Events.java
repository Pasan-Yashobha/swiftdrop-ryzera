package lk.swiftdrop.document.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Builds event messages the way other services publish them, for the consumer tests. */
public final class Events {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Events() { }

    /** A full, valid envelope around {@code data}. */
    public static ObjectNode envelope(UUID eventId, String type, String correlationId, ObjectNode data) {
        ObjectNode root = JSON.createObjectNode();
        if (eventId != null) root.put("eventId", eventId.toString());
        root.put("eventType", type);
        root.put("version", 1);
        root.put("occurredAt", "2026-11-11T10:00:00Z");
        root.put("producer", "parcel-service");
        if (correlationId != null) root.put("correlationId", correlationId);
        root.put("aggregateId", data.path("parcelId").asText("unknown"));
        root.set("data", data);
        return root;
    }

    /** The data of a delivered parcel that carries everything a receipt needs: 300 + 200 + 50 + 137.50 - 68.75. */
    public static ObjectNode deliveredData(UUID parcelId, String customerId) {
        ObjectNode data = JSON.createObjectNode();
        data.put("parcelId", parcelId.toString());
        data.put("trackingNo", "SD-2641-7788");
        if (customerId != null) data.put("customerId", customerId);
        data.put("courierId", UUID.randomUUID().toString());
        data.put("fromStatus", "OUT_FOR_DELIVERY");
        data.put("toStatus", "DELIVERED");
        ObjectNode pricing = data.putObject("pricing");
        pricing.put("baseAmount", 300.00);
        pricing.put("weightAmount", 200.00);
        pricing.put("sizeAmount", "50.00");
        pricing.put("expressAmount", "137.50");
        pricing.put("discountAmount", 68.75);
        pricing.put("totalAmount", "618.75");
        return data;
    }

    /** Only the ids: the receipt data has to come from the Parcel service. */
    public static ObjectNode bareData(UUID parcelId) {
        ObjectNode data = JSON.createObjectNode();
        data.put("parcelId", parcelId.toString());
        data.put("toStatus", "DELIVERED");
        return data;
    }

    public static String json(ObjectNode node) {
        return node.toString();
    }
}
