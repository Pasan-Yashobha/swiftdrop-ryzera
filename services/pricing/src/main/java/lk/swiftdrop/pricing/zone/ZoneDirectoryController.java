package lk.swiftdrop.pricing.zone;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ZoneDirectoryController {
    private final ZoneRepository zones;

    public ZoneDirectoryController(ZoneRepository zones) { this.zones = zones; }

    @GetMapping("/api/v1/pricing/zones")
    public DirectoryPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                              @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size) {
        var result = zones.findByActiveTrue(PageRequest.of(page, size, Sort.by("code")));
        return new DirectoryPage(result.map(zone -> new Entry(zone.getId(), zone.getCode(), zone.getName()))
                .getContent(), page, size, result.getTotalElements());
    }

    public record Entry(UUID id, String code, String name) { }
    public record DirectoryPage(List<Entry> items, int page, int size, long totalElements) { }
}
