package lk.swiftdrop.merchant.merchant;

import jakarta.validation.Valid;
import lk.swiftdrop.merchant.merchant.dto.MerchantResponse;
import lk.swiftdrop.merchant.merchant.dto.RegisterMerchantRequest;
import lk.swiftdrop.merchant.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A merchant user's own profile (CONTRACT section 5.1). */
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;
    private final CurrentMerchantProvider currentMerchant;

    public MerchantController(MerchantService merchantService, CurrentMerchantProvider currentMerchant) {
        this.merchantService = merchantService;
        this.currentMerchant = currentMerchant;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantResponse register(@Valid @RequestBody RegisterMerchantRequest request) {
        return MerchantMapper.toResponse(merchantService.register(CurrentUser.current().userId(), request.toProfile()));
    }

    @GetMapping("/me")
    public MerchantResponse me() {
        return MerchantMapper.toResponse(currentMerchant.requireMerchant());
    }

    @PutMapping("/me")
    public MerchantResponse update(@Valid @RequestBody RegisterMerchantRequest request) {
        return MerchantMapper.toResponse(merchantService.update(CurrentUser.current().userId(), request.toProfile()));
    }

    @PostMapping("/me/resubmit")
    public MerchantResponse resubmit() {
        return MerchantMapper.toResponse(merchantService.resubmit(CurrentUser.current().userId()));
    }
}
