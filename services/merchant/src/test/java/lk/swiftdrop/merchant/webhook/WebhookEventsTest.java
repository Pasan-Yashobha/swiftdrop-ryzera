package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import lk.swiftdrop.merchant.common.BadRequestException;
import lk.swiftdrop.merchant.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookEventsTest {

    private static final List<String> ALL_SUPPORTED = List.of(
            "parcel.created", "parcel.status.picked_up", "parcel.status.in_transit",
            "parcel.status.out_for_delivery", "parcel.status.delivered", "parcel.status.failed",
            "parcel.status.rescheduled", "parcel.status.returned", "parcel.cancelled");

    @Test
    void wildcardAloneIsAccepted() {
        assertThat(WebhookEvents.normalise(List.of("*"))).containsExactly("*");
    }

    @Test
    void everySupportedEventTypeIsAccepted() {
        assertThat(WebhookEvents.normalise(ALL_SUPPORTED)).containsExactlyElementsOf(ALL_SUPPORTED);
    }

    @Test
    void eventsAreTrimmedAndDeduplicatedKeepingTheirOrder() {
        List<String> normalised = WebhookEvents.normalise(
                List.of(" parcel.status.delivered", "parcel.created", "parcel.status.delivered ", "parcel.cancelled"));

        assertThat(normalised).containsExactly("parcel.status.delivered", "parcel.created", "parcel.cancelled");
    }

    @Test
    void duplicatedWildcardCollapsesToOne() {
        assertThat(WebhookEvents.normalise(List.of("*", " * "))).containsExactly("*");
    }

    @Test
    void wildcardCannotBeMixedWithOtherEvents() {
        assertThatThrownBy(() -> WebhookEvents.normalise(List.of("*", "parcel.created")))
                .isInstanceOfSatisfying(BadRequestException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getDetails()).singleElement().satisfies(detail -> {
                        assertThat(detail.field()).isEqualTo("events");
                        assertThat(detail.message()).isEqualTo("'*' cannot be combined with other event types");
                    });
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"parcel.assigned", "parcel.status.created", "PARCEL.CREATED", "parcel.*", "merchant.approved",
            "parcel.status.", "parcel.created,parcel.cancelled"})
    void unsupportedEventTypesAreRejectedWithoutEchoingThem(String event) {
        assertThatThrownBy(() -> WebhookEvents.normalise(List.of("parcel.created", event)))
                .isInstanceOfSatisfying(BadRequestException.class, e -> {
                    assertThat(e.getDetails()).singleElement().satisfies(detail -> {
                        assertThat(detail.field()).isEqualTo("events");
                        assertThat(detail.message()).isEqualTo("must be '*' or a list of supported event types");
                    });
                    assertThat(e.getMessage()).doesNotContain(event);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankEventTypesAreRejected(String event) {
        assertThatThrownBy(() -> WebhookEvents.normalise(List.of(event))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void nullEntriesAndEmptyListsAreRejected() {
        assertThatThrownBy(() -> WebhookEvents.normalise(Arrays.asList("parcel.created", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> WebhookEvents.normalise(List.of())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void eventsAreStoredCommaSeparatedAndReadBack() {
        List<String> events = List.of("parcel.created", "parcel.cancelled");

        String stored = WebhookEvents.join(events);

        assertThat(stored).isEqualTo("parcel.created,parcel.cancelled");
        assertThat(WebhookEvents.split(stored)).containsExactlyElementsOf(events);
        assertThat(WebhookEvents.split("*")).containsExactly("*");
    }

    @Test
    void theLongestPossibleSubscriptionFitsTheColumn() {
        assertThat(WebhookEvents.join(ALL_SUPPORTED).length()).isLessThanOrEqualTo(500);
    }
}
