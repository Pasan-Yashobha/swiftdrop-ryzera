package lk.swiftdrop.pricing.zone;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/zones")
public class ZoneController {
    private final ZoneService zones;

    public ZoneController(ZoneService zones) {
        this.zones = zones;
    }

    @PostMapping
    public ResponseEntity<ZoneResponse> create(@Valid @RequestBody ZoneRequest request) {
        ZoneResponse zone = zones.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/zones/" + zone.id())).body(zone);
    }

    @GetMapping
    public ZoneService.ZonePage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return zones.list(page, size);
    }

    @GetMapping("/{id}")
    public ZoneResponse get(@PathVariable UUID id) {
        return zones.get(id);
    }

    @PutMapping("/{id}")
    public ZoneResponse update(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return zones.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        zones.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
