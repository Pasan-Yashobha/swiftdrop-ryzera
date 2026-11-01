package lk.swiftdrop.merchant.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.order.dto.OrderAddress;
import lk.swiftdrop.merchant.order.dto.OrderParcel;
import lk.swiftdrop.merchant.order.dto.OrderRecipient;
import org.springframework.stereotype.Component;

/** Valid order requests and ready-made stored orders for the tests of this package. */
@Component
class OrderFixtures {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    private final MerchantOrderRepository orders;

    OrderFixtures(MerchantOrderRepository orders) {
        this.orders = orders;
    }

    static CreateOrderRequest request(String externalRef) {
        return new CreateOrderRequest(externalRef,
                new OrderRecipient("Nimali Perera", "+94771234567"),
                new OrderAddress("12 Galle Rd", "Colombo", "00300"),
                new OrderAddress("45 Temple St", "Kandy", "20000"),
                new OrderParcel(new BigDecimal("2.5"), ParcelSize.SMALL, "Phone case"),
                "Call on arrival");
    }

    /** The request as a JSON-ready map, so that a test can change or remove single fields. */
    static Map<String, Object> payload(String externalRef) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (externalRef != null) {
            payload.put("externalRef", externalRef);
        }
        payload.put("recipient", new LinkedHashMap<>(Map.of("name", "Nimali Perera", "phone", "+94771234567")));
        payload.put("pickup", new LinkedHashMap<>(
                Map.of("addressLine", "12 Galle Rd", "city", "Colombo", "postalCode", "00300")));
        payload.put("dropoff", new LinkedHashMap<>(
                Map.of("addressLine", "45 Temple St", "city", "Kandy", "postalCode", "20000")));
        payload.put("parcel", new LinkedHashMap<>(
                Map.of("weightKg", 2.5, "size", "SMALL", "description", "Phone case")));
        payload.put("notes", "Call on arrival");
        return payload;
    }

    static Map<String, Object> payload() {
        return payload("ORDER-1001");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> section(Map<String, Object> payload, String name) {
        return (Map<String, Object>) payload.get(name);
    }

    Seed order(Merchant merchant) {
        return new Seed(merchant);
    }

    /** Builds and stores an order directly in the database, bypassing the creation flow. */
    final class Seed {

        private final Merchant merchant;
        private OrderStatus status = OrderStatus.CREATED;
        private boolean sandbox;
        private String externalRef;
        private String idempotencyKey;
        private String requestHash;
        private String trackingNo = "SD" + SEQUENCE.incrementAndGet();
        private String parcelId = "parcel-" + SEQUENCE.get();
        private String recipientName = "Nimali Perera";
        private Instant createdAt = Instant.parse("2026-03-01T10:00:00Z");
        private Instant lastEventAt;
        private OrderSource source = OrderSource.API;

        private Seed(Merchant merchant) {
            this.merchant = merchant;
        }

        Seed status(OrderStatus status) {
            this.status = status;
            return this;
        }

        Seed sandbox() {
            this.sandbox = true;
            this.trackingNo = SandboxSimulator.TRACKING_PREFIX + (100_000_000 + SEQUENCE.incrementAndGet());
            this.parcelId = null;
            return this;
        }

        Seed externalRef(String externalRef) {
            this.externalRef = externalRef;
            return this;
        }

        Seed idempotencyKey(String key, String hash) {
            this.idempotencyKey = key;
            this.requestHash = hash;
            return this;
        }

        Seed trackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
            return this;
        }

        Seed parcelId(String parcelId) {
            this.parcelId = parcelId;
            return this;
        }

        Seed recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        Seed createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        Seed lastEventAt(Instant lastEventAt) {
            this.lastEventAt = lastEventAt;
            return this;
        }

        Seed source(OrderSource source) {
            this.source = source;
            return this;
        }

        /** A placeholder that never got completed: no tracking number, status PENDING. */
        MerchantOrder savePlaceholder() {
            return orders.save(placeholder());
        }

        MerchantOrder save() {
            MerchantOrder order = placeholder();
            order.complete(parcelId, trackingNo, new Money(new BigDecimal("450.00"), "LKR"), createdAt);
            if (status != OrderStatus.CREATED || lastEventAt != null) {
                order.moveTo(status, null, lastEventAt, createdAt);
            }
            return orders.save(order);
        }

        private MerchantOrder placeholder() {
            CreateOrderRequest request = new CreateOrderRequest(externalRef,
                    new OrderRecipient(recipientName, "+94771234567"),
                    new OrderAddress("12 Galle Rd", "Colombo", "00300"),
                    new OrderAddress("45 Temple St", "Kandy", "20000"),
                    new OrderParcel(new BigDecimal("2.5"), ParcelSize.SMALL, "Phone case"), null);
            return MerchantOrder.placeholder(UUID.randomUUID(), merchant.getId(), request, source, sandbox,
                    idempotencyKey, requestHash, createdAt);
        }
    }
}
