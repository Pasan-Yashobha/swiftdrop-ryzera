package lk.swiftdrop.merchant.merchant;

import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ForbiddenException;
import lk.swiftdrop.merchant.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Resolves "my merchant" for portal endpoints. The merchant is looked up by ownership
 * ({@code owner_user_id = JWT subject}); portal URLs never contain a merchant id, so a user can only ever
 * reach their own data.
 */
@Component
public class CurrentMerchantProvider {

    private final MerchantService merchantService;

    public CurrentMerchantProvider(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    /** @throws lk.swiftdrop.merchant.common.NotFoundException {@code MERCHANT_NOT_REGISTERED} when the user owns none */
    public Merchant requireMerchant() {
        return merchantService.getByOwner(CurrentUser.current().userId());
    }

    /** @throws ForbiddenException {@code MERCHANT_NOT_APPROVED} unless the merchant is APPROVED */
    public Merchant requireApprovedMerchant() {
        Merchant merchant = requireMerchant();
        if (!merchant.isApproved()) {
            throw new ForbiddenException(ErrorCode.MERCHANT_NOT_APPROVED,
                    "Your merchant account must be approved before you can use this feature");
        }
        return merchant;
    }
}
