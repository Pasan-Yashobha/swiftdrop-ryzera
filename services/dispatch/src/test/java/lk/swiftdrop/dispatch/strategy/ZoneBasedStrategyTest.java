package lk.swiftdrop.dispatch.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lk.swiftdrop.dispatch.client.PricingZoneClient;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ZoneBasedStrategyTest {

    private PricingZoneClient pricingZoneClient;
    private ZoneBasedStrategy strategy;

    private final UUID colomboZone = UUID.randomUUID();
    private final UUID gampahaZone = UUID.randomUUID();
    private final UUID kandyZone = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pricingZoneClient = Mockito.mock(PricingZoneClient.class);
        strategy = new ZoneBasedStrategy(pricingZoneClient);
    }

    @Test
    @DisplayName("Strategy type should be ZONE_BASED")
    void strategyType() {
        assertThat(strategy.type()).isEqualTo(StrategyType.ZONE_BASED);
    }

    @Test
    @DisplayName("Prefers courier whose home zone matches parcel pickup zone")
    void prefersExactZoneMatch() {
        CourierWorkload colomboCourier = new CourierWorkload(UUID.randomUUID(), colomboZone, 3, 8, true, null);
        CourierWorkload gampahaCourier = new CourierWorkload(UUID.randomUUID(), gampahaZone, 1, 8, true, null);

        UnassignedParcel parcel = createParcel(colomboZone);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(colomboCourier, gampahaCourier));

        assertThat(chosen).isPresent();
        assertThat(chosen.get().getCourierId()).isEqualTo(colomboCourier.getCourierId());
    }

    @Test
    @DisplayName("Falls back to neighbouring zone courier when exact zone has no free couriers")
    void fallsBackToNeighbouringZone() {
        when(pricingZoneClient.getNeighbours(colomboZone)).thenReturn(Set.of(gampahaZone));

        // Colombo courier is at max capacity (8/8)
        CourierWorkload colomboCourier = new CourierWorkload(UUID.randomUUID(), colomboZone, 8, 8, true, null);
        CourierWorkload gampahaCourier = new CourierWorkload(UUID.randomUUID(), gampahaZone, 2, 8, true, null);
        CourierWorkload kandyCourier = new CourierWorkload(UUID.randomUUID(), kandyZone, 1, 8, true, null);

        UnassignedParcel parcel = createParcel(colomboZone);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(colomboCourier, gampahaCourier, kandyCourier));

        assertThat(chosen).isPresent();
        assertThat(chosen.get().getCourierId()).isEqualTo(gampahaCourier.getCourierId());
    }

    @Test
    @DisplayName("Returns empty when neither exact zone nor neighbouring zones have available couriers")
    void returnsEmptyWhenNoZoneMatch() {
        when(pricingZoneClient.getNeighbours(colomboZone)).thenReturn(Set.of(gampahaZone));

        // Only Kandy courier available (not a neighbour of Colombo)
        CourierWorkload kandyCourier = new CourierWorkload(UUID.randomUUID(), kandyZone, 1, 8, true, null);

        UnassignedParcel parcel = createParcel(colomboZone);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(kandyCourier));

        assertThat(chosen).isEmpty();
    }

    private UnassignedParcel createParcel(UUID zone) {
        return new UnassignedParcel(UUID.randomUUID(), "SWD-ZONE-TEST", zone, "Area", "MEDIUM", 2.0, Instant.now());
    }
}
