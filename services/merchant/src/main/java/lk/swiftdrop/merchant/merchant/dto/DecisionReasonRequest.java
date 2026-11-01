package lk.swiftdrop.merchant.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.PlainText;

/** Body of the reject and suspend endpoints. */
public record DecisionReasonRequest(@NotBlank @Size(max = 500) @PlainText String reason) {

    public DecisionReasonRequest {
        reason = Inputs.trim(reason);
    }
}
