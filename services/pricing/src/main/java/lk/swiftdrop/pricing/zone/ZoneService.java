package lk.swiftdrop.pricing.zone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ZoneService {
    private final ZoneRepository zones;

    public ZoneService(ZoneRepository zones) {
        this.zones = zones;
    }

    @Transactional
    public ZoneResponse create(ZoneRequest request) {
        return ZoneResponse.from(zones.saveAndFlush(new Zone(request.code(), request.name().strip(),
                request.description(), Instant.now())));
    }

    public ZonePage list(int page, int size) {
        Page<Zone> result = zones.findAll(PageRequest.of(page, size, Sort.by("code")));
        return new ZonePage(result.map(ZoneResponse::from).getContent(), page, size, result.getTotalElements());
    }

    public ZoneResponse get(UUID id) {
        return ZoneResponse.from(find(id));
    }

    @Transactional
    public ZoneResponse update(UUID id, ZoneRequest request) {
        Zone zone = find(id);
        zone.update(request.code(), request.name().strip(), request.description(), Instant.now());
        zones.flush();
        return ZoneResponse.from(zone);
    }

    @Transactional
    public void deactivate(UUID id) {
        Zone zone = find(id);
        if (zone.isActive()) {
            zone.deactivate(Instant.now());
            zones.flush();
        }
    }

    private Zone find(UUID id) {
        return zones.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found"));
    }

    public record ZonePage(List<ZoneResponse> items, int page, int size, long totalElements) {
    }
}
