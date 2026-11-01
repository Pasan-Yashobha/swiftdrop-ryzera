package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderStatusTest {

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "FAILED", "RETURNED", "CANCELLED"})
    void endStatesAreTerminal(OrderStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.isInProgress()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class,
            names = {"CREATED", "ASSIGNED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "RESCHEDULED"})
    void bookedParcelsOnTheirWayAreInProgress(OrderStatus status) {
        assertThat(status.isInProgress()).isTrue();
        assertThat(status.isTerminal()).isFalse();
    }

    @Test
    void placeholderIsNeitherTerminalNorInProgress() {
        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
        assertThat(OrderStatus.PENDING.isInProgress()).isFalse();
    }

    @Test
    void sandboxWalksThroughTheDeliveryInOrder() {
        List<OrderStatus> visited = new ArrayList<>(List.of(OrderStatus.CREATED));
        Optional<OrderStatus> next = OrderStatus.CREATED.nextInSandboxLifecycle();
        while (next.isPresent()) {
            visited.add(next.get());
            next = next.get().nextInSandboxLifecycle();
        }

        assertThat(visited).containsExactly(OrderStatus.CREATED, OrderStatus.ASSIGNED, OrderStatus.PICKED_UP,
                OrderStatus.IN_TRANSIT, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class,
            names = {"PENDING", "DELIVERED", "FAILED", "RESCHEDULED", "RETURNED", "CANCELLED"})
    void noSandboxStepAfterTheseStatuses(OrderStatus status) {
        assertThat(status.nextInSandboxLifecycle()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"picked_up,PICKED_UP", "PICKED_UP,PICKED_UP", "out-for-delivery,OUT_FOR_DELIVERY",
            "' delivered ',DELIVERED", "created,CREATED", "failed,FAILED", "rescheduled,RESCHEDULED",
            "returned,RETURNED", "cancelled,CANCELLED", "in_transit,IN_TRANSIT", "assigned,ASSIGNED"})
    void parsesParcelStatuses(String parcelStatus, OrderStatus expected) {
        assertThat(OrderStatus.fromParcelStatus(parcelStatus)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "teleported", "", "picked up", "picked_up_"})
    void unknownParcelStatusesAndThePlaceholderAreNotParsed(String parcelStatus) {
        assertThat(OrderStatus.fromParcelStatus(parcelStatus)).isEmpty();
    }

    @Test
    void spellingsMatchTheFrontendModel() {
        // frontend/projects/merchant-portal/src/app/core/models/order.model.ts, ORDER_STATUSES
        assertThat(Arrays.stream(OrderStatus.values()).map(Enum::name)).containsExactly("PENDING", "CREATED",
                "ASSIGNED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "RESCHEDULED",
                "RETURNED", "CANCELLED");
        assertThat(Stream.of(ParcelSize.values()).map(Enum::name)).containsExactly("SMALL", "MEDIUM", "LARGE");
        assertThat(Stream.of(OrderSource.values()).map(Enum::name)).containsExactly("API", "PORTAL", "BULK");
    }
}
