package lk.swiftdrop.pricing.estimate;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API (X-Internal-Token, see SecurityConfig). The Parcel service calls it once a booking is
 * confirmed; retries with the same quote are safe and return the same snapshot.
 */
@RestController
public class InternalQuoteController {
    private final QuoteAcceptanceService acceptance;

    public InternalQuoteController(QuoteAcceptanceService acceptance) {
        this.acceptance = acceptance;
    }

    @PostMapping("/internal/quotes/{quoteId}/accept")
    public AcceptedQuote accept(@PathVariable UUID quoteId, @RequestBody(required = false) AcceptRequest body) {
        return acceptance.accept(quoteId, body == null ? null : body.parcelId());
    }

    /** The body is optional; {@code parcelId} links the quote to the booked parcel. */
    public record AcceptRequest(UUID parcelId) { }
}
