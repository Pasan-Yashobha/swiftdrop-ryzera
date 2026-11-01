package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lk.swiftdrop.merchant.merchant.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/** The order read model fed with parcel events, end to end through the handler and the real database. */
class ParcelEventHandlerTest extends AbstractOrderTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @Autowired
    private ParcelEventHandler handler;

    @Autowired
    private ProcessedEventRepository processedEvents;

    private MerchantOrder order;

    @BeforeEach
    void liveOrder() {
        order = orderFixtures.order(merchant).trackingNo("SD100").parcelId("P-100").createdAt(T0).save();
    }

    // ---- building events ----

    private static Instant minutes(int n) {
        return T0.plusSeconds(60L * n);
    }

    private Map<String, Object> data(UUID merchantId, String trackingNo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantId", merchantId.toString());
        data.put("trackingNo", trackingNo);
        data.put("parcelId", "P-100");
        return data;
    }

    private byte[] event(String eventId, String eventType, Instant occurredAt, Map<String, Object> data)
            throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("version", 1);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", "parcel-service");
        envelope.put("correlationId", "corr-1");
        envelope.put("aggregateId", "P-100");
        envelope.put("data", data);
        return objectMapper.writeValueAsBytes(envelope);
    }

    private void deliver(String eventId, String eventType, Instant occurredAt) throws Exception {
        handler.handle(event(eventId, eventType, occurredAt, data(merchant.getId(), "SD100")));
    }

    private MerchantOrder reloaded() {
        return orders.findById(order.getId()).orElseThrow();
    }

    private List<OrderStatusHistory> timeline() {
        return history.findByOrderIdOrderByOccurredAtAscIdAsc(order.getId());
    }

    // ---- status changes ----

    @Test
    void fullLifecycleUpdatesTheStatusAndWritesTheTimeline() throws Exception {
        deliver("e1", "parcel.assigned", minutes(1));
        deliver("e2", "parcel.status.picked_up", minutes(2));
        deliver("e3", "parcel.status.in_transit", minutes(3));
        deliver("e4", "parcel.status.out_for_delivery", minutes(4));
        deliver("e5", "parcel.status.delivered", minutes(5));

        MerchantOrder updated = reloaded();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(updated.getLastEventAt()).isEqualTo(minutes(5));
        assertThat(updated.getFailureReason()).isNull();
        assertThat(updated.getUpdatedAt()).isAfter(T0);
        assertThat(timeline()).extracting(OrderStatusHistory::getEventId, OrderStatusHistory::getFromStatus,
                        OrderStatusHistory::getToStatus, OrderStatusHistory::getOccurredAt)
                .containsExactly(
                        tuple("e1", OrderStatus.CREATED, OrderStatus.ASSIGNED, minutes(1)),
                        tuple("e2", OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, minutes(2)),
                        tuple("e3", OrderStatus.PICKED_UP, OrderStatus.IN_TRANSIT, minutes(3)),
                        tuple("e4", OrderStatus.IN_TRANSIT, OrderStatus.OUT_FOR_DELIVERY, minutes(4)),
                        tuple("e5", OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, minutes(5)));
        assertThat(processedEvents.count()).isEqualTo(5);
    }

    @Test
    void failedDeliveryKeepsTheReasonUntilTheNextEventClearsIt() throws Exception {
        Map<String, Object> failure = data(merchant.getId(), "SD100");
        failure.put("failureReason", "Recipient not at home");
        handler.handle(event("e1", "parcel.status.failed", minutes(1), failure));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(reloaded().getFailureReason()).isEqualTo("Recipient not at home");
        assertThat(timeline()).singleElement().satisfies(entry -> assertThat(entry.getNote())
                .isEqualTo("Recipient not at home"));

        deliver("e2", "parcel.status.rescheduled", minutes(2));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.RESCHEDULED);
        assertThat(reloaded().getFailureReason()).isNull();
    }

    @Test
    void longFailureReasonIsShortenedToWhatTheColumnHolds() throws Exception {
        Map<String, Object> failure = data(merchant.getId(), "SD100");
        failure.put("failureReason", "x".repeat(500));

        handler.handle(event("e1", "parcel.status.failed", minutes(1), failure));

        assertThat(reloaded().getFailureReason()).hasSize(300);
    }

    @Test
    void cancellationMovesTheOrderToCancelled() throws Exception {
        deliver("e1", "parcel.cancelled", minutes(1));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(timeline()).extracting(OrderStatusHistory::getToStatus).containsExactly(OrderStatus.CANCELLED);
    }

    @Test
    void statusNamesAreMatchedIgnoringCase() throws Exception {
        deliver("e1", "parcel.status.RETURNED", minutes(1));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    // ---- order of events and repeats ----

    @Test
    void duplicateEventIdIsIgnored() throws Exception {
        deliver("e1", "parcel.status.picked_up", minutes(1));
        Map<String, Object> changedData = data(merchant.getId(), "SD100");
        byte[] sameIdOtherContent = event("e1", "parcel.status.delivered", minutes(9), changedData);

        handler.handle(sameIdOtherContent);
        deliver("e1", "parcel.status.picked_up", minutes(1));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(timeline()).hasSize(1);
        assertThat(processedEvents.count()).isEqualTo(1);
    }

    @Test
    void lateEventIsRecordedButDoesNotMoveTheStatusBackwards() throws Exception {
        deliver("e4", "parcel.status.out_for_delivery", minutes(4));
        deliver("e2", "parcel.status.picked_up", minutes(2));

        MerchantOrder updated = reloaded();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(updated.getLastEventAt()).isEqualTo(minutes(4));
        assertThat(timeline()).extracting(OrderStatusHistory::getEventId, OrderStatusHistory::getFromStatus,
                        OrderStatusHistory::getToStatus)
                .containsExactly(
                        tuple("e2", null, OrderStatus.PICKED_UP),
                        tuple("e4", OrderStatus.CREATED, OrderStatus.OUT_FOR_DELIVERY));
    }

    @Test
    void lateEventDoesNotChangeTheFailureReasonEither() throws Exception {
        Map<String, Object> failure = data(merchant.getId(), "SD100");
        failure.put("failureReason", "Address not found");
        handler.handle(event("e5", "parcel.status.failed", minutes(5), failure));

        Map<String, Object> older = data(merchant.getId(), "SD100");
        older.put("failureReason", "Something older");
        handler.handle(event("e1", "parcel.status.rescheduled", minutes(1), older));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(reloaded().getFailureReason()).isEqualTo("Address not found");
    }

    @Test
    void eventWithTheTimeOfTheLastOneStillMovesTheOrder() throws Exception {
        deliver("e1", "parcel.status.picked_up", minutes(3));
        deliver("e2", "parcel.status.in_transit", minutes(3));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
        assertThat(timeline()).hasSize(2);
    }

    @Test
    void sameEventDeliveredConcurrentlyIsAppliedOnce() throws Exception {
        byte[] body = event("e1", "parcel.status.picked_up", minutes(1), data(merchant.getId(), "SD100"));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> deliveries = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                deliveries.add(pool.submit(() -> {
                    start.await();
                    handler.handle(body);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> delivery : deliveries) {
                assertThatCode(() -> delivery.get(30, TimeUnit.SECONDS)).doesNotThrowAnyException();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(timeline()).hasSize(1);
        assertThat(processedEvents.count()).isEqualTo(1);
        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.PICKED_UP);
    }

    // ---- which orders are touched ----

    @Test
    void eventOfAnotherMerchantNeverTouchesTheOrder() throws Exception {
        Merchant other = fixtures.approvedMerchant("owner-2");

        handler.handle(event("e1", "parcel.status.delivered", minutes(1), data(other.getId(), "SD100")));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(reloaded().getLastEventAt()).isNull();
        assertThat(history.count()).isZero();
        assertThat(processedEvents.existsById("e1")).isFalse();
    }

    @Test
    void eventForAnUnknownOrderIsIgnoredAndNotRemembered() throws Exception {
        Map<String, Object> unknown = data(merchant.getId(), "SD-UNKNOWN");
        unknown.put("parcelId", "P-UNKNOWN");

        handler.handle(event("e1", "parcel.status.delivered", minutes(1), unknown));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.count()).isZero();
        assertThat(processedEvents.existsById("e1")).isFalse();
    }

    @Test
    void orderIsFoundByParcelIdWhenTheEventHasNoTrackingNumber() throws Exception {
        Map<String, Object> noTracking = data(merchant.getId(), "ignored");
        noTracking.remove("trackingNo");

        handler.handle(event("e1", "parcel.status.picked_up", minutes(1), noTracking));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.PICKED_UP);
    }

    @Test
    void orderIsFoundByParcelIdWhenTheTrackingNumberIsUnknown() throws Exception {
        handler.handle(event("e1", "parcel.status.picked_up", minutes(1), data(merchant.getId(), "SD-RENAMED")));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.PICKED_UP);
    }

    @Test
    void parcelIdOfAnotherMerchantsOrderIsNotAMatch() throws Exception {
        Merchant other = fixtures.approvedMerchant("owner-2");

        handler.handle(event("e1", "parcel.status.picked_up", minutes(1), data(other.getId(), "SD-RENAMED")));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void sandboxOrdersIgnoreBrokerEvents() throws Exception {
        MerchantOrder sandbox = orderFixtures.order(merchant).sandbox().trackingNo("SBX000000001").save();

        handler.handle(event("e1", "parcel.status.delivered", minutes(1), data(merchant.getId(), "SBX000000001")));

        assertThat(orders.findById(sandbox.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(history.count()).isZero();
    }

    // ---- events that are not status changes ----

    @Test
    void parcelCreatedBackfillsTheParcelIdOnly() throws Exception {
        MerchantOrder withoutParcel = orderFixtures.order(merchant).trackingNo("SD200").parcelId(null).save();
        Map<String, Object> created = data(merchant.getId(), "SD200");
        created.put("parcelId", "P-200");

        handler.handle(event("e1", "parcel.created", minutes(1), created));

        MerchantOrder updated = orders.findById(withoutParcel.getId()).orElseThrow();
        assertThat(updated.getParcelId()).isEqualTo("P-200");
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(updated.getLastEventAt()).isNull();
        assertThat(history.count()).isZero();
    }

    @Test
    void parcelCreatedDoesNotOverwriteAnExistingParcelId() throws Exception {
        Map<String, Object> created = data(merchant.getId(), "SD100");
        created.put("parcelId", "P-OTHER");

        handler.handle(event("e1", "parcel.created", minutes(1), created));

        assertThat(reloaded().getParcelId()).isEqualTo("P-100");
        assertThat(history.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"parcel.status.teleported", "parcel.status.pending", "parcel.status.", "parcel.deleted",
            "merchant.approved"})
    void eventsThatDoNotMapToAStatusChangeAreRememberedAndIgnored(String eventType) throws Exception {
        deliver("e1", eventType, minutes(1));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(reloaded().getLastEventAt()).isNull();
        assertThat(history.count()).isZero();
        assertThat(processedEvents.existsById("e1")).isTrue();
    }

    @Test
    void eventsThatNameNeitherAMerchantNorAParcelAreIgnoredAndNotRemembered() throws Exception {
        Map<String, Object> noMerchant = data(merchant.getId(), "SD100");
        noMerchant.remove("merchantId");
        noMerchant.remove("parcelId");
        handler.handle(event("e1", "parcel.status.delivered", minutes(1), noMerchant));
        Map<String, Object> badMerchant = data(merchant.getId(), "SD100");
        badMerchant.put("merchantId", "not-a-uuid");
        badMerchant.remove("parcelId");
        handler.handle(event("e2", "parcel.status.delivered", minutes(2), badMerchant));
        Map<String, Object> numericMerchant = data(merchant.getId(), "SD100");
        numericMerchant.put("merchantId", 42);
        numericMerchant.remove("parcelId");
        handler.handle(event("e3", "parcel.status.delivered", minutes(3), numericMerchant));
        handler.handle("{\"eventId\":\"e4\",\"eventType\":\"parcel.status.delivered\",\"occurredAt\":\"2026-03-01T10:04:00Z\"}"
                .getBytes(StandardCharsets.UTF_8));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(processedEvents.count()).isZero();
    }

    @Test
    void dispatchEventsThatCarryOnlyTheParcelIdFindTheirOrder() throws Exception {
        Map<String, Object> assigned = new LinkedHashMap<>();
        assigned.put("parcelId", "P-100");
        assigned.put("courierId", "C-1");
        handler.handle(event("e1", "parcel.assigned", minutes(1), assigned));
        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.ASSIGNED);

        Map<String, Object> cancelled = new LinkedHashMap<>();
        cancelled.put("parcelId", "P-100");
        cancelled.put("reason", "customer request");
        handler.handle(event("e2", "parcel.cancelled", minutes(2), cancelled));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(timeline()).hasSize(2);
        assertThat(processedEvents.count()).isEqualTo(2);
    }

    @Test
    void dispatchEventForAnUnknownParcelIsIgnored() throws Exception {
        Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("parcelId", "P-NOBODY");

        handler.handle(event("e1", "parcel.cancelled", minutes(1), unknown));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(processedEvents.count()).isZero();
    }

    @Test
    void unknownFieldsOfTheEnvelopeAreIgnored() throws Exception {
        String body = "{\"eventId\":\"e1\",\"eventType\":\"parcel.status.delivered\",\"occurredAt\":\"2026-03-01T10:05:00Z\","
                + "\"brandNewField\":{\"a\":1},\"data\":{\"merchantId\":\"" + merchant.getId()
                + "\",\"trackingNo\":\"SD100\",\"somethingElse\":[1,2]}}";

        handler.handle(body.getBytes(StandardCharsets.UTF_8));

        assertThat(reloaded().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void occurredAtMayCarryAnOffsetAndFractionalSeconds() throws Exception {
        String body = "{\"eventId\":\"e1\",\"eventType\":\"parcel.status.delivered\","
                + "\"occurredAt\":\"2026-03-01T15:35:00.250+05:30\",\"data\":{\"merchantId\":\"" + merchant.getId()
                + "\",\"trackingNo\":\"SD100\"}}";

        handler.handle(body.getBytes(StandardCharsets.UTF_8));

        assertThat(reloaded().getLastEventAt()).isEqualTo(Instant.parse("2026-03-01T10:05:00.250Z"));
    }

    // ---- messages that are not events ----

    @ParameterizedTest
    @ValueSource(strings = {
            "not json at all",
            "",
            "   ",
            "[]",
            "\"just a string\"",
            "42",
            "{",
            "{}",
            "{\"eventType\":\"parcel.assigned\",\"occurredAt\":\"2026-03-01T10:00:00Z\"}",
            "{\"eventId\":\"e1\",\"occurredAt\":\"2026-03-01T10:00:00Z\"}",
            "{\"eventId\":\"e1\",\"eventType\":\"parcel.assigned\"}",
            "{\"eventId\":\"e1\",\"eventType\":\"parcel.assigned\",\"occurredAt\":\"yesterday\"}",
            "{\"eventId\":\"e1\",\"eventType\":\"parcel.assigned\",\"occurredAt\":1700000000}",
            "{\"eventId\":\"  \",\"eventType\":\"parcel.assigned\",\"occurredAt\":\"2026-03-01T10:00:00Z\"}",
            "{\"eventId\":7,\"eventType\":\"parcel.assigned\",\"occurredAt\":\"2026-03-01T10:00:00Z\"}",
            "{\"eventId\":\"e1\",\"eventType\":null,\"occurredAt\":\"2026-03-01T10:00:00Z\"}"})
    void malformedMessagesAreRejected(String body) {
        assertThatThrownBy(() -> handler.handle(body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);

        assertThat(processedEvents.count()).isZero();
        assertThat(history.count()).isZero();
    }

    @Test
    void identifiersLongerThanTheirColumnsAreRejected() {
        String longId = "{\"eventId\":\"" + "e".repeat(65)
                + "\",\"eventType\":\"parcel.assigned\",\"occurredAt\":\"2026-03-01T10:00:00Z\"}";
        String longType = "{\"eventId\":\"e1\",\"eventType\":\"" + "t".repeat(101)
                + "\",\"occurredAt\":\"2026-03-01T10:00:00Z\"}";

        assertThatThrownBy(() -> handler.handle(longId.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> handler.handle(longType.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectionMessageNeverContainsTheMessageContent() {
        String secret = "pii-marker-0771234567";

        assertThatThrownBy(() -> handler.handle(("{\"eventId\":\"" + secret + "\"").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(secret));
        assertThatThrownBy(() -> handler.handle(
                ("{\"eventId\":\"" + secret + "\",\"occurredAt\":\"" + secret + "\",\"eventType\":\"x\"}")
                        .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(secret));
    }
}
