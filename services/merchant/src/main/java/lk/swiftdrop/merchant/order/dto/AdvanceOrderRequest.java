package lk.swiftdrop.merchant.order.dto;

import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.PlainText;

/**
 * Optional body of the sandbox advance endpoint. Without a body, or without an outcome, the order takes its next
 * step; {@code reason} is only used with {@link AdvanceOutcome#FAILED}.
 */
public record AdvanceOrderRequest(AdvanceOutcome outcome, @Size(max = 300) @PlainText String reason) {

    public AdvanceOrderRequest {
        outcome = outcome == null ? AdvanceOutcome.NEXT : outcome;
        reason = Inputs.trimToNull(reason);
    }

    public static AdvanceOrderRequest next() {
        return new AdvanceOrderRequest(AdvanceOutcome.NEXT, null);
    }

    /** The reason is free text, and Spring logs request bodies at DEBUG. */
    @Override
    public String toString() {
        return "AdvanceOrderRequest[outcome=" + outcome + "]";
    }
}
