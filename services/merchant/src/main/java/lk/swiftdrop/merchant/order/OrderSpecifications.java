package lk.swiftdrop.merchant.order;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Builds the query of an order list. Every list is scoped to one merchant, whatever else is filtered. */
final class OrderSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private OrderSpecifications() {
    }

    static Specification<MerchantOrder> matching(UUID merchantId, OrderFilter filter) {
        return Specification.allOf(
                ownedBy(merchantId),
                inEnvironment(filter.sandbox()),
                hasStatus(filter.status()),
                hasExternalRef(filter.externalRef()),
                matchesSearch(filter.search()),
                createdFrom(filter.from()),
                createdUntil(filter.to()));
    }

    private static Specification<MerchantOrder> ownedBy(UUID merchantId) {
        return (root, query, builder) -> builder.equal(root.get("merchantId"), merchantId);
    }

    private static Specification<MerchantOrder> inEnvironment(SandboxMode mode) {
        return (root, query, builder) -> switch (mode) {
            case LIVE -> builder.isFalse(root.get("sandbox"));
            case SANDBOX -> builder.isTrue(root.get("sandbox"));
            case ALL -> null;
        };
    }

    private static Specification<MerchantOrder> hasStatus(OrderStatus status) {
        return (root, query, builder) -> status == null
                ? builder.notEqual(root.get("status"), OrderStatus.PENDING)
                : builder.equal(root.get("status"), status);
    }

    private static Specification<MerchantOrder> hasExternalRef(String externalRef) {
        return (root, query, builder) -> externalRef == null ? null : builder.equal(root.get("externalRef"), externalRef);
    }

    /** Case-insensitive "contains" on tracking number, external reference and recipient name. */
    private static Specification<MerchantOrder> matchesSearch(String search) {
        return (root, query, builder) -> {
            if (!StringUtils.hasText(search)) {
                return null;
            }
            String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
            return builder.or(
                    builder.like(builder.lower(root.get("trackingNo")), pattern, LIKE_ESCAPE),
                    builder.like(builder.lower(root.get("externalRef")), pattern, LIKE_ESCAPE),
                    builder.like(builder.lower(root.get("recipientName")), pattern, LIKE_ESCAPE));
        };
    }

    private static Specification<MerchantOrder> createdFrom(LocalDate from) {
        return (root, query, builder) -> from == null ? null
                : builder.greaterThanOrEqualTo(root.<Instant>get("createdAt"), startOfDay(from));
    }

    /** The end date is inclusive, so orders up to the start of the following day are included. */
    private static Specification<MerchantOrder> createdUntil(LocalDate to) {
        return (root, query, builder) -> to == null ? null
                : builder.lessThan(root.<Instant>get("createdAt"), startOfDay(to.plusDays(1)));
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
