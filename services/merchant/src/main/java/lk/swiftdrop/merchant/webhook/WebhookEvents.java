package lk.swiftdrop.merchant.webhook;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lk.swiftdrop.merchant.common.ApiError;
import lk.swiftdrop.merchant.common.BadRequestException;

/** The event types a merchant can subscribe to, and how a subscription is validated and stored. */
final class WebhookEvents {

    static final String ALL = "*";

    private static final Set<String> SUPPORTED = Set.of(
            "parcel.created",
            "parcel.status.picked_up",
            "parcel.status.in_transit",
            "parcel.status.out_for_delivery",
            "parcel.status.delivered",
            "parcel.status.failed",
            "parcel.status.rescheduled",
            "parcel.status.returned",
            "parcel.cancelled");

    private static final String SEPARATOR = ",";

    private WebhookEvents() {
    }

    /**
     * Trims, removes duplicates and checks a requested subscription: either exactly {@code *} or a non-empty
     * list of supported event types.
     *
     * @throws BadRequestException {@code VALIDATION_FAILED} naming the {@code events} field
     */
    static List<String> normalise(List<String> requested) {
        Set<String> events = new LinkedHashSet<>();
        for (String event : requested) {
            events.add(event == null ? "" : event.trim());
        }
        if (events.contains(ALL)) {
            if (events.size() > 1) {
                throw invalid("'*' cannot be combined with other event types");
            }
            return List.of(ALL);
        }
        if (events.isEmpty() || !SUPPORTED.containsAll(events)) {
            throw invalid("must be '*' or a list of supported event types");
        }
        return List.copyOf(events);
    }

    static String join(List<String> events) {
        return String.join(SEPARATOR, events);
    }

    static List<String> split(String stored) {
        return Arrays.asList(stored.split(SEPARATOR));
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException("Invalid webhook events", List.of(new ApiError.Detail("events", message)));
    }
}
