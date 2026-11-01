package lk.swiftdrop.merchant.credential;

import lk.swiftdrop.merchant.credential.dto.PingResponse;
import lk.swiftdrop.merchant.merchant.MerchantService;
import lk.swiftdrop.merchant.security.MerchantApiPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets an integrator test a credential: {@code GET /merchant-api/ping} says whose credential it is. */
@RestController
public class MerchantApiPingController {

    private final MerchantService merchants;

    public MerchantApiPingController(MerchantService merchants) {
        this.merchants = merchants;
    }

    @GetMapping(MerchantApiPaths.PING)
    public PingResponse ping(@AuthenticationPrincipal MerchantApiPrincipal principal) {
        return new PingResponse(principal.merchantId(), merchants.getById(principal.merchantId()).getBusinessName(),
                principal.environment(), Scopes.canonical(principal.scopes()));
    }
}
