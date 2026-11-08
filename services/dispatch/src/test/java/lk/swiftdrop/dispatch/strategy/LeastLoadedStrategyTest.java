package lk.swiftdrop.dispatch.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeastLoadedStrategyTest {

    private final LeastLoadedStrategy strategy = new LeastLoadedStrategy();

    @Test
    @DisplayName("Strategy type should be LEAST_LOADED")
    void strategyType() {
        assertThat(strategy.type()).isEqualTo(StrategyType.LEAST_LOADED);
    }

    @Test
    @DisplayName("Picks courier with lowest activeDeliveries")
    void picksLowestActive() {
        CourierWorkload c1 = new CourierWorkload(UUID.randomUUID(), null, 5, 8, true, Instant.now());
        CourierWorkload c2 = new CourierWorkload(UUID.randomUUID(), null, 2, 8, true, Instant.now());
        CourierWorkload c3 = new CourierWorkload(UUID.randomUUID(), null, 4, 8, true, Instant.now());

        UnassignedParcel parcel = createParcel(null);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(c1, c2, c3));

        assertThat(chosen).isPresent();
        assertThat(chosen.get().getCourierId()).isEqualTo(c2.getCourierId());
    }

    @Test
    @DisplayName("Breaks ties by selecting the courier who waited longest (oldest lastAssignedAt)")
    void breaksTiesByOldestAssignment() {
        Instant now = Instant.now();
        CourierWorkload c1 = new CourierWorkload(UUID.randomUUID(), null, 3, 8, true, now.minusSeconds(100));
        CourierWorkload c2 = new CourierWorkload(UUID.randomUUID(), null, 3, 8, true, now.minusSeconds(500)); // Waited longer

        UnassignedParcel parcel = createParcel(null);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(c1, c2));

        assertThat(chosen).isPresent();
        assertThat(chosen.get().getCourierId()).isEqualTo(c2.getCourierId());
    }

    @Test
    @DisplayName("Courier with null lastAssignedAt (never assigned) wins tie against courier with timestamp")
    void nullLastAssignedAtWinsTie() {
        CourierWorkload c1 = new CourierWorkload(UUID.randomUUID(), null, 1, 8, true, Instant.now().minusSeconds(1000));
        CourierWorkload c2 = new CourierWorkload(UUID.randomUUID(), null, 1, 8, true, null); // Never assigned

        UnassignedParcel parcel = createParcel(null);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(c1, c2));

        assertThat(chosen).isPresent();
        assertThat(chosen.get().getCourierId()).isEqualTo(c2.getCourierId());
    }

    @Test
    @DisplayName("Discards couriers that are unavailable or at maximum capacity")
    void discardsUnavailableAndFull() {
        CourierWorkload full = new CourierWorkload(UUID.randomUUID(), null, 8, 8, true, null);
        CourierWorkload offline = new CourierWorkload(UUID.randomUUID(), null, 0, 8, false, null);
        CourierWorkload available = new CourierWorkload(UUID.randomUUID(), null, 6, 8, true, null);

        UnassignedParcel parcel = createParcel(null);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(full, offline, available));

        assertThat(chosen).isPresent();
        assertThat(chosen.get().getCourierId()).isEqualTo(available.getCourierId());
    }

    @Test
    @DisplayName("Returns empty when all couriers are full or unavailable")
    void returnsEmptyWhenNoneEligible() {
        CourierWorkload full = new CourierWorkload(UUID.randomUUID(), null, 8, 8, true, null);
        CourierWorkload offline = new CourierWorkload(UUID.randomUUID(), null, 1, 8, false, null);

        UnassignedParcel parcel = createParcel(null);
        Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, List.of(full, offline));

        assertThat(chosen).isEmpty();
    }

    private UnassignedParcel createParcel(UUID zone) {
        return new UnassignedParcel(UUID.randomUUID(), "SWD-TEST-1", zone, "Test Area", "SMALL", 1.0, Instant.now());
    }
}
