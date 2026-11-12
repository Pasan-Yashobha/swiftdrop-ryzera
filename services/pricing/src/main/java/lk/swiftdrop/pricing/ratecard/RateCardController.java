package lk.swiftdrop.pricing.ratecard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/rate-cards")
public class RateCardController {
    private final RateCardService cards;

    public RateCardController(RateCardService cards) {
        this.cards = cards;
    }

    @PostMapping
    public ResponseEntity<RateCardResponse> create(@Valid @RequestBody RateCardRequest request) {
        RateCardResponse card = cards.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/rate-cards/" + card.id())).body(card);
    }

    @GetMapping
    public RateCardService.RateCardPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return cards.list(page, size);
    }

    @GetMapping("/{id}")
    public RateCardResponse get(@PathVariable UUID id) {
        return cards.get(id);
    }

    @PutMapping("/{id}")
    public RateCardResponse update(@PathVariable UUID id, @Valid @RequestBody RateCardRequest request) {
        return cards.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        cards.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
