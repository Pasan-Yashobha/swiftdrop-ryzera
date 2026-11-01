package lk.swiftdrop.merchant.merchant;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.swiftdrop.merchant.common.PageRequests;
import lk.swiftdrop.merchant.common.PageResponse;
import lk.swiftdrop.merchant.merchant.dto.AdminMerchantResponse;
import lk.swiftdrop.merchant.merchant.dto.DecisionReasonRequest;
import lk.swiftdrop.merchant.merchant.dto.RateLimitRequest;
import lk.swiftdrop.merchant.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Merchant review and management for administrators (CONTRACT section 5.2). */
@RestController
@RequestMapping("/api/v1/admin/merchants")
public class AdminMerchantController {

    private final MerchantService merchantService;

    public AdminMerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public PageResponse<AdminMerchantResponse> list(
            @RequestParam(required = false) MerchantStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(merchantService.search(status, search, pageable), MerchantMapper::toAdminResponse);
    }

    @GetMapping("/{id}")
    public AdminMerchantResponse get(@PathVariable UUID id) {
        return MerchantMapper.toAdminResponse(merchantService.getById(id));
    }

    @PostMapping("/{id}/approve")
    public AdminMerchantResponse approve(@PathVariable UUID id) {
        return MerchantMapper.toAdminResponse(merchantService.approve(id, adminId()));
    }

    @PostMapping("/{id}/reject")
    public AdminMerchantResponse reject(@PathVariable UUID id, @Valid @RequestBody DecisionReasonRequest request) {
        return MerchantMapper.toAdminResponse(merchantService.reject(id, adminId(), request.reason()));
    }

    @PostMapping("/{id}/suspend")
    public AdminMerchantResponse suspend(@PathVariable UUID id, @Valid @RequestBody DecisionReasonRequest request) {
        return MerchantMapper.toAdminResponse(merchantService.suspend(id, adminId(), request.reason()));
    }

    @PostMapping("/{id}/reinstate")
    public AdminMerchantResponse reinstate(@PathVariable UUID id) {
        return MerchantMapper.toAdminResponse(merchantService.reinstate(id, adminId()));
    }

    @PutMapping("/{id}/rate-limit")
    public AdminMerchantResponse setRateLimit(@PathVariable UUID id, @Valid @RequestBody RateLimitRequest request) {
        return MerchantMapper.toAdminResponse(merchantService.setRateLimit(id, request.requestsPerMinute()));
    }

    private static String adminId() {
        return CurrentUser.current().userId();
    }
}
