package lk.swiftdrop.pricing.ratecard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.pricing.zone.ZoneRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class RateCardService {
    private final RateCardRepository cards;
    private final ZoneRepository zones;

    public RateCardService(RateCardRepository cards, ZoneRepository zones) {
        this.cards = cards;
        this.zones = zones;
    }

    @Transactional
    public RateCardResponse create(RateCardRequest request) {
        requireActiveZone(request.originZoneId());
        requireActiveZone(request.destinationZoneId());
        return save(new RateCard(request, Instant.now()));
    }

    @Transactional
    public RateCardResponse update(UUID id, RateCardRequest request) {
        RateCard card = find(id);
        requireActiveZone(request.originZoneId());
        requireActiveZone(request.destinationZoneId());
        card.update(request, Instant.now());
        return save(card);
    }

    @Transactional
    public void deactivate(UUID id) {
        RateCard card = find(id);
        if (card.isActive()) {
            card.deactivate(Instant.now());
            cards.flush();
        }
    }

    public RateCardResponse get(UUID id) {
        return RateCardResponse.from(find(id));
    }

    public RateCardPage list(int page, int size) {
        Page<RateCard> result = cards.findAll(PageRequest.of(page, size,
                Sort.by("validFrom").descending().and(Sort.by("id"))));
        return new RateCardPage(result.map(RateCardResponse::from).getContent(), page, size,
                result.getTotalElements());
    }

    private RateCard find(UUID id) {
        return cards.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Rate card not found"));
    }

    private void requireActiveZone(UUID id) {
        if (zones.findById(id).filter(zone -> zone.isActive()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing active zone");
        }
    }

    private RateCardResponse save(RateCard card) {
        try {
            return RateCardResponse.from(cards.saveAndFlush(card));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rate card conflicts with existing data. Check the route and effective dates.", exception);
        }
    }

    public record RateCardPage(List<RateCardResponse> items, int page, int size, long totalElements) {
    }
}
