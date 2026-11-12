package lk.swiftdrop.pricing.promo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/promo-codes")
public class PromoCodeController {
    private final PromoCodeService promos;
    public PromoCodeController(PromoCodeService promos) { this.promos = promos; }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> create(@Valid @RequestBody PromoCodeRequest request) {
        var promo = promos.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/promo-codes/" + promo.id())).body(promo);
    }

    @GetMapping
    public PromoCodeService.PromoPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return promos.list(page, size);
    }

    @GetMapping("/{id}")
    public PromoCodeResponse get(@PathVariable UUID id) { return promos.get(id); }

    @PutMapping("/{id}")
    public PromoCodeResponse update(@PathVariable UUID id, @Valid @RequestBody PromoCodeRequest request) {
        return promos.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        promos.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
