package lk.swiftdrop.merchant.order;

import java.time.LocalDate;

/**
 * Optional conditions of an order list; a null condition is not applied.
 *
 * @param status      only this status; without it, internal {@code PENDING} placeholders are hidden
 * @param search      text contained in the tracking number, external reference or recipient name
 * @param externalRef exactly this external reference
 * @param sandbox     live orders, sandbox orders or both
 * @param from        orders created on or after this UTC date
 * @param to          orders created on or before this UTC date
 */
public record OrderFilter(OrderStatus status, String search, String externalRef, SandboxMode sandbox,
                          LocalDate from, LocalDate to) {

    /** The filters of the portal order list. */
    public static OrderFilter forPortal(OrderStatus status, String search, SandboxMode sandbox, LocalDate from,
                                        LocalDate to) {
        return new OrderFilter(status, search, null, sandbox, from, to);
    }

    /** The filters of the merchant API order list, which is limited to the environment of the credential. */
    public static OrderFilter forApi(OrderStatus status, String externalRef, boolean sandboxCredential) {
        return new OrderFilter(status, null, externalRef, SandboxMode.of(sandboxCredential), null, null);
    }
}
