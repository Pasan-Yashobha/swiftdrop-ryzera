package com.swiftdrop.reporting.testsupport;

import com.swiftdrop.reporting.dto.EventEnvelope;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Exact test fixture for Section 7 of Reporting_Service_Build_Brief.md:
 *
 * 5 parcels, day D (2026-11-05) and D+1 (2026-11-06), 31 events in total.
 * - P1: merchant M1, zone Z2, courier C1. OFD 09:00, delivered 09:40 on D (40 min).
 * - P2: M1, Z2, C1. OFD 09:00, delivered 10:20 on D (80 min).
 * - P3: M1, Z2, C2. OFD 11:00, failed 11:30, returned 16:00 on D.
 * - P4: other customer (null merchant), Z2, C1. failed 13:40 on D, rescheduled. OFD 09:30, delivered 10:00 on D+1 (30 min).
 * - P5: Z2, C2. created, assigned, cancelled on D.
 * Plus courier availability changed for courier C1.
 *
 * Total = 6 + 6 + 7 + 8 + 3 + 1 = 31 events.
 */
public class TestEventFixture {

    public static final LocalDate DAY_D   = LocalDate.of(2026, 11, 5);
    public static final LocalDate DAY_D_1 = LocalDate.of(2026, 11, 6);

    public static final UUID M1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID Z2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID C1 = UUID.fromString("33333333-3333-3333-3333-333333333331");
    public static final UUID C2 = UUID.fromString("33333333-3333-3333-3333-333333333332");

    public static final UUID P1 = UUID.fromString("44444444-4444-4444-4444-444444444441");
    public static final UUID P2 = UUID.fromString("44444444-4444-4444-4444-444444444442");
    public static final UUID P3 = UUID.fromString("44444444-4444-4444-4444-444444444443");
    public static final UUID P4 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID P5 = UUID.fromString("44444444-4444-4444-4444-444444444445");

    public static List<EventEnvelope> buildAll31Events() {
        List<EventEnvelope> events = new ArrayList<>();

        // ── P1 (6 events) ────────────────────────────────────────────────────
        events.add(createEnvelope("parcel.created", "2026-11-05T08:00:00Z",
                Map.of("parcelId", P1, "trackingNo", "TRK-P1", "merchantId", M1, "dropoffZoneId", Z2, "fee", new BigDecimal("500.00"))));
        events.add(createEnvelope("parcel.assigned", "2026-11-05T08:15:00Z",
                Map.of("parcelId", P1, "courierId", C1)));
        events.add(createEnvelope("parcel.status.picked_up", "2026-11-05T08:30:00Z",
                Map.of("parcelId", P1, "courierId", C1)));
        events.add(createEnvelope("parcel.status.in_transit", "2026-11-05T08:45:00Z",
                Map.of("parcelId", P1, "courierId", C1)));
        events.add(createEnvelope("parcel.status.out_for_delivery", "2026-11-05T09:00:00Z",
                Map.of("parcelId", P1, "courierId", C1)));
        events.add(createEnvelope("parcel.status.delivered", "2026-11-05T09:40:00Z",
                Map.of("parcelId", P1, "courierId", C1))); // 40 min

        // ── P2 (6 events) ────────────────────────────────────────────────────
        events.add(createEnvelope("parcel.created", "2026-11-05T08:00:00Z",
                Map.of("parcelId", P2, "trackingNo", "TRK-P2", "merchantId", M1, "dropoffZoneId", Z2, "fee", new BigDecimal("450.00"))));
        events.add(createEnvelope("parcel.assigned", "2026-11-05T08:15:00Z",
                Map.of("parcelId", P2, "courierId", C1)));
        events.add(createEnvelope("parcel.status.picked_up", "2026-11-05T08:30:00Z",
                Map.of("parcelId", P2, "courierId", C1)));
        events.add(createEnvelope("parcel.status.in_transit", "2026-11-05T08:45:00Z",
                Map.of("parcelId", P2, "courierId", C1)));
        events.add(createEnvelope("parcel.status.out_for_delivery", "2026-11-05T09:00:00Z",
                Map.of("parcelId", P2, "courierId", C1)));
        events.add(createEnvelope("parcel.status.delivered", "2026-11-05T10:20:00Z",
                Map.of("parcelId", P2, "courierId", C1))); // 80 min

        // ── P3 (7 events) ────────────────────────────────────────────────────
        events.add(createEnvelope("parcel.created", "2026-11-05T08:00:00Z",
                Map.of("parcelId", P3, "trackingNo", "TRK-P3", "merchantId", M1, "dropoffZoneId", Z2, "fee", new BigDecimal("600.00"))));
        events.add(createEnvelope("parcel.assigned", "2026-11-05T08:15:00Z",
                Map.of("parcelId", P3, "courierId", C2)));
        events.add(createEnvelope("parcel.status.picked_up", "2026-11-05T08:30:00Z",
                Map.of("parcelId", P3, "courierId", C2)));
        events.add(createEnvelope("parcel.status.in_transit", "2026-11-05T08:45:00Z",
                Map.of("parcelId", P3, "courierId", C2)));
        events.add(createEnvelope("parcel.status.out_for_delivery", "2026-11-05T11:00:00Z",
                Map.of("parcelId", P3, "courierId", C2)));
        events.add(createEnvelope("parcel.status.failed", "2026-11-05T11:30:00Z",
                Map.of("parcelId", P3, "courierId", C2)));
        events.add(createEnvelope("parcel.status.returned", "2026-11-05T16:00:00Z",
                Map.of("parcelId", P3)));

        // ── P4 (8 events) ────────────────────────────────────────────────────
        // Merchant is null ("other customer")
        events.add(createEnvelope("parcel.created", "2026-11-05T08:00:00Z",
                Map.of("parcelId", P4, "trackingNo", "TRK-P4", "dropoffZoneId", Z2, "fee", new BigDecimal("350.00"))));
        events.add(createEnvelope("parcel.assigned", "2026-11-05T08:15:00Z",
                Map.of("parcelId", P4, "courierId", C1)));
        events.add(createEnvelope("parcel.status.picked_up", "2026-11-05T08:30:00Z",
                Map.of("parcelId", P4, "courierId", C1)));
        events.add(createEnvelope("parcel.status.in_transit", "2026-11-05T08:45:00Z",
                Map.of("parcelId", P4, "courierId", C1)));
        events.add(createEnvelope("parcel.status.failed", "2026-11-05T13:40:00Z",
                Map.of("parcelId", P4, "courierId", C1)));
        events.add(createEnvelope("parcel.status.rescheduled", "2026-11-05T14:00:00Z",
                Map.of("parcelId", P4)));
        events.add(createEnvelope("parcel.status.out_for_delivery", "2026-11-06T09:30:00Z",
                Map.of("parcelId", P4, "courierId", C1)));
        events.add(createEnvelope("parcel.status.delivered", "2026-11-06T10:00:00Z",
                Map.of("parcelId", P4, "courierId", C1))); // 30 min

        // ── P5 (3 events) ────────────────────────────────────────────────────
        events.add(createEnvelope("parcel.created", "2026-11-05T08:00:00Z",
                Map.of("parcelId", P5, "trackingNo", "TRK-P5", "dropoffZoneId", Z2, "fee", new BigDecimal("300.00"))));
        events.add(createEnvelope("parcel.assigned", "2026-11-05T08:15:00Z",
                Map.of("parcelId", P5, "courierId", C2)));
        events.add(createEnvelope("parcel.cancelled", "2026-11-05T08:30:00Z",
                Map.of("parcelId", P5)));

        // ── Courier availability snapshot (1 event) ──────────────────────────
        events.add(createEnvelope("courier.availability.changed", "2026-11-05T12:00:00Z",
                Map.of("courierId", C1, "available", true, "activeDeliveries", 1)));

        return events;
    }

    private static EventEnvelope createEnvelope(String eventType, String occurredAtUtc, Map<String, Object> payload) {
        // Derive stable UUID based on eventType and occurredAt for idempotent reproducibility
        UUID eventId = UUID.nameUUIDFromBytes((eventType + "_" + occurredAtUtc + "_" + payload.toString()).getBytes());
        return new EventEnvelope(
                eventId,
                eventType,
                Instant.parse(occurredAtUtc),
                UUID.randomUUID().toString(),
                new HashMap<>(payload)
        );
    }
}
