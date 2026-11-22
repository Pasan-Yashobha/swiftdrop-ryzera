package lk.swiftdrop.dispatch.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lk.swiftdrop.dispatch.client.PricingZoneClient;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import org.springframework.stereotype.Component;

/**
 * Zone-based strategy:
 * 1. Couriers whose home zone matches the parcel's pickup zone first (least-loaded among them).
 * 2. If none free, couriers in neighbouring zones (least-loaded among them).
 * 3. If still none, parcel remains unassigned.
 */
@Component
public class ZoneBasedStrategy extends AbstractCapacityAwareStrategy {

    private static final Comparator<CourierWorkload> LEAST_LOADED_COMPARATOR = Comparator
            .comparingInt(CourierWorkload::getActiveDeliveries)
            .thenComparing(CourierWorkload::getLastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final PricingZoneClient pricingZoneClient;

    public ZoneBasedStrategy(PricingZoneClient pricingZoneClient) {
        this.pricingZoneClient = pricingZoneClient;
    }

    @Override
    public StrategyType type() {
        return StrategyType.ZONE_BASED;
    }

    @Override
    protected Optional<CourierWorkload> pick(UnassignedParcel parcel, List<CourierWorkload> eligible) {
        UUID pickupZone = parcel.pickupZone();
        if (pickupZone == null) {
            // If parcel has no zone specified, pick least loaded
            return eligible.stream().min(LEAST_LOADED_COMPARATOR);
        }

        // 1. Couriers in the exact same zone
        List<CourierWorkload> sameZone = eligible.stream()
                .filter(c -> pickupZone.equals(c.getZoneId()))
                .toList();
        if (!sameZone.isEmpty()) {
            return sameZone.stream().min(LEAST_LOADED_COMPARATOR);
        }

        // 2. Couriers in neighbouring zones
        Set<UUID> neighbours = pricingZoneClient.getNeighbours(pickupZone);
        if (neighbours != null && !neighbours.isEmpty()) {
            List<CourierWorkload> neighbouringCouriers = eligible.stream()
                    .filter(c -> c.getZoneId() != null && neighbours.contains(c.getZoneId()))
                    .toList();
            if (!neighbouringCouriers.isEmpty()) {
                return neighbouringCouriers.stream().min(LEAST_LOADED_COMPARATOR);
            }
        }

        // 3. None available in same or neighbouring zones
        return Optional.empty();
    }
}

