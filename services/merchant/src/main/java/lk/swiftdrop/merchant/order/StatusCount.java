package lk.swiftdrop.merchant.order;

/** Number of orders in one status, the row type of the statistics query. */
public record StatusCount(OrderStatus status, long total) {
}
