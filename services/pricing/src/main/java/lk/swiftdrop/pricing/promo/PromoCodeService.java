package lk.swiftdrop.pricing.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PromoCodeService {
    private final PromoCodeRepository promos;
    private final Clock clock;

    public PromoCodeService(PromoCodeRepository promos, Clock clock) {
        this.promos = promos;
        this.clock = clock;
    }

    @Transactional
    public PromoCodeResponse create(PromoCodeRequest request) {
        return save(new PromoCode(request, clock.instant()));
    }

    @Transactional
    public PromoCodeResponse update(UUID id, PromoCodeRequest request) {
        PromoCode promo = find(id);
        if (request.maxUses() != null && request.maxUses() < promo.getUsedCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum uses cannot be below redeemed uses");
        }
        promo.update(request, clock.instant());
        return save(promo);
    }

    public PromoCodeResponse get(UUID id) { return PromoCodeResponse.from(find(id)); }

    public PromoPage list(int page, int size) {
        var result = promos.findAll(PageRequest.of(page, size, Sort.by("code")));
        return new PromoPage(result.map(PromoCodeResponse::from).getContent(), page, size, result.getTotalElements());
    }

    @Transactional
    public void deactivate(UUID id) {
        PromoCode promo = find(id);
        if (promo.isActive()) {
            promo.deactivate(clock.instant());
            promos.flush();
        }
    }

    public AppliedDiscount calculate(String code, BigDecimal subtotal, LocalDate date) {
        if (code == null || code.isBlank()) return new AppliedDiscount(null, new BigDecimal("0.00"));
        PromoCode promo = promos.findByCode(code.toUpperCase(Locale.ROOT)).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Promo code is invalid or unavailable"));
        boolean exhausted = promo.getMaxUses() != null && promo.getUsedCount() >= promo.getMaxUses();
        if (!promo.isActive() || date.isBefore(promo.getValidFrom())
                || (promo.getValidTo() != null && !date.isBefore(promo.getValidTo())) || exhausted) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Promo code is invalid or unavailable");
        }
        if (subtotal.compareTo(promo.getMinOrderValue()) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This fare is below the promo's minimum order value");
        }
        BigDecimal amount = switch (promo.getDiscountType()) {
            case PERCENT -> subtotal.multiply(promo.getDiscountValue()).movePointLeft(2);
            case FLAT -> promo.getDiscountValue();
        };
        return new AppliedDiscount(promo.getId(), amount.min(subtotal).setScale(2, RoundingMode.HALF_UP));
    }

    private PromoCode find(UUID id) {
        return promos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo code not found"));
    }

    private PromoCodeResponse save(PromoCode promo) {
        try { return PromoCodeResponse.from(promos.saveAndFlush(promo)); }
        catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promo code conflicts with an existing record", exception);
        }
    }

    public record AppliedDiscount(UUID promoId, BigDecimal amount) { }
    public record PromoPage(List<PromoCodeResponse> items, int page, int size, long totalElements) { }
}
