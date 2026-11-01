package lk.swiftdrop.merchant.order;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.swiftdrop.merchant.order.dto.DailyOrderCount;
import lk.swiftdrop.merchant.order.dto.OrderStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard figures. Only live orders count: sandbox orders are for testing an integration. */
@Service
@Transactional(readOnly = true)
public class OrderStatsService {

    private static final int DAYS = 7;

    private final MerchantOrderRepository orders;
    private final Clock clock;

    OrderStatsService(MerchantOrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    public OrderStats statsFor(UUID merchantId) {
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        orders.countLiveByStatus(merchantId, OrderStatus.PENDING)
                .forEach(count -> byStatus.put(count.status(), count.total()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long inProgress = byStatus.entrySet().stream()
                .filter(entry -> entry.getKey().isInProgress())
                .mapToLong(Map.Entry::getValue)
                .sum();
        return new OrderStats(total, byStatus, byStatus.getOrDefault(OrderStatus.DELIVERED, 0L),
                byStatus.getOrDefault(OrderStatus.FAILED, 0L), inProgress, lastDays(merchantId));
    }

    /** Orders per UTC day for today and the six days before, oldest first, days without orders included. */
    private List<DailyOrderCount> lastDays(UUID merchantId) {
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate first = today.minusDays(DAYS - 1L);
        Map<LocalDate, Long> perDay = orders
                .findLiveCreationTimesSince(merchantId, OrderStatus.PENDING, first.atStartOfDay(ZoneOffset.UTC).toInstant())
                .stream()
                .collect(Collectors.groupingBy(created -> created.atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()));
        List<DailyOrderCount> days = new ArrayList<>(DAYS);
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            days.add(new DailyOrderCount(day, perDay.getOrDefault(day, 0L)));
        }
        return days;
    }
}
