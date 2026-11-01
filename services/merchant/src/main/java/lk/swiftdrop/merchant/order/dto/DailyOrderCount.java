package lk.swiftdrop.merchant.order.dto;

import java.time.LocalDate;

/** Orders created on one UTC day. */
public record DailyOrderCount(LocalDate date, long count) {
}
