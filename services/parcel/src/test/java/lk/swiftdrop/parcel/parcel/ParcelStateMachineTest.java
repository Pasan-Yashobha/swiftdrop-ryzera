package lk.swiftdrop.parcel.parcel;

import lk.swiftdrop.parcel.common.ConflictException;
import lk.swiftdrop.parcel.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelStateMachineTest {

    private ParcelStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ParcelStateMachine();
    }

    @ParameterizedTest(name = "Valid transition: {0} -> {1}")
    @CsvSource({
            "CREATED, ASSIGNED",
            "CREATED, CANCELLED",
            "ASSIGNED, PICKED_UP",
            "ASSIGNED, RESCHEDULED",
            "ASSIGNED, CANCELLED",
            "ASSIGNED, FAILED",
            "PICKED_UP, IN_TRANSIT",
            "PICKED_UP, OUT_FOR_DELIVERY",
            "PICKED_UP, FAILED",
            "PICKED_UP, RETURNED",
            "IN_TRANSIT, OUT_FOR_DELIVERY",
            "IN_TRANSIT, RESCHEDULED",
            "IN_TRANSIT, FAILED",
            "IN_TRANSIT, RETURNED",
            "OUT_FOR_DELIVERY, DELIVERED",
            "OUT_FOR_DELIVERY, FAILED",
            "OUT_FOR_DELIVERY, RESCHEDULED",
            "OUT_FOR_DELIVERY, RETURNED",
            "FAILED, RESCHEDULED",
            "FAILED, OUT_FOR_DELIVERY",
            "FAILED, RETURNED",
            "RESCHEDULED, ASSIGNED",
            "RESCHEDULED, OUT_FOR_DELIVERY",
            "RESCHEDULED, CANCELLED",
            "RESCHEDULED, RETURNED"
    })
    void testValidTransitions(ParcelStatus from, ParcelStatus to) {
        assertThat(stateMachine.isAllowed(from, to)).isTrue();
        assertThatCode(() -> stateMachine.validateTransition(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "Invalid transition: {0} -> {1}")
    @CsvSource({
            "CREATED, DELIVERED",
            "CREATED, PICKED_UP",
            "CREATED, IN_TRANSIT",
            "CREATED, RETURNED",
            "PICKED_UP, CREATED",
            "PICKED_UP, DELIVERED",
            "IN_TRANSIT, CREATED",
            "IN_TRANSIT, ASSIGNED",
            "FAILED, DELIVERED",
            "FAILED, CREATED"
    })
    void testInvalidTransitions(ParcelStatus from, ParcelStatus to) {
        assertThat(stateMachine.isAllowed(from, to)).isFalse();
        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("DELIVERED is terminal and rejects any subsequent transition")
    void testDeliveredIsTerminal() {
        assertThat(ParcelStatus.DELIVERED.isTerminal()).isTrue();

        for (ParcelStatus target : ParcelStatus.values()) {
            if (target != ParcelStatus.DELIVERED) {
                assertThat(stateMachine.isAllowed(ParcelStatus.DELIVERED, target)).isFalse();
                assertThatThrownBy(() -> stateMachine.validateTransition(ParcelStatus.DELIVERED, target))
                        .isInstanceOf(ConflictException.class)
                        .hasMessageContaining("terminal status 'DELIVERED'");
            }
        }
    }

    @Test
    @DisplayName("RETURNED is terminal")
    void testReturnedIsTerminal() {
        assertThat(ParcelStatus.RETURNED.isTerminal()).isTrue();

        for (ParcelStatus target : ParcelStatus.values()) {
            if (target != ParcelStatus.RETURNED) {
                assertThat(stateMachine.isAllowed(ParcelStatus.RETURNED, target)).isFalse();
                assertThatThrownBy(() -> stateMachine.validateTransition(ParcelStatus.RETURNED, target))
                        .isInstanceOf(ConflictException.class)
                        .hasMessageContaining("terminal status 'RETURNED'");
            }
        }
    }

    @Test
    @DisplayName("CANCELLED is terminal")
    void testCancelledIsTerminal() {
        assertThat(ParcelStatus.CANCELLED.isTerminal()).isTrue();

        for (ParcelStatus target : ParcelStatus.values()) {
            if (target != ParcelStatus.CANCELLED) {
                assertThat(stateMachine.isAllowed(ParcelStatus.CANCELLED, target)).isFalse();
                assertThatThrownBy(() -> stateMachine.validateTransition(ParcelStatus.CANCELLED, target))
                        .isInstanceOf(ConflictException.class)
                        .hasMessageContaining("terminal status 'CANCELLED'");
            }
        }
    }

    @Test
    @DisplayName("Transition to identical state is idempotent")
    void testSameStateIsIdempotent() {
        assertThatCode(() -> stateMachine.validateTransition(ParcelStatus.CREATED, ParcelStatus.CREATED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.validateTransition(ParcelStatus.IN_TRANSIT, ParcelStatus.IN_TRANSIT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Null status validation throws ConflictException")
    void testNullStatusValidation() {
        assertThatThrownBy(() -> stateMachine.validateTransition(null, ParcelStatus.CREATED))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> stateMachine.validateTransition(ParcelStatus.CREATED, null))
                .isInstanceOf(ConflictException.class);
    }
}
