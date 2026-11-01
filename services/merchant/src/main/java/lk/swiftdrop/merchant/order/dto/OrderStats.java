package lk.swiftdrop.merchant.order.dto;

import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.order.OrderStatus;

/**
 * Dashboard figures of a merchant's live orders.
 *
 * @param byStatus  order count per status; statuses without orders are left out
 * @param last7Days today and the six days before, oldest first, with zero counts included
 */
public record OrderStats(
        long total,
        Map<OrderStatus, Long> byStatus,
        long delivered,
        long failed,
        long inProgress,
        List<DailyOrderCount> last7Days) {
}
