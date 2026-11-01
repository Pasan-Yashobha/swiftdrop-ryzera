package lk.swiftdrop.merchant.order.dto;

import java.math.BigDecimal;

/** An amount with two decimals and its ISO currency code (LKR). */
public record Money(BigDecimal amount, String currency) {
}
