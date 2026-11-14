package lk.swiftdrop.tracking.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Parcel lifecycle as owned by the Parcel service (M4), plus how each status shows on the five-stop transit line
 * (01 Created, 02 Picked up, 03 In transit, 04 Out for delivery, 05 Delivered).
 * ASSIGNED is internal: the public page shows it as CREATED.
 */
public enum ParcelStatus {

    CREATED(1, "Created", Tone.PROGRESS, false),
    ASSIGNED(1, "Created", Tone.PROGRESS, false),
    PICKED_UP(2, "Picked up", Tone.PROGRESS, false),
    IN_TRANSIT(3, "In transit", Tone.PROGRESS, false),
    OUT_FOR_DELIVERY(4, "Out for delivery", Tone.PROGRESS, false),
    DELIVERED(5, "Delivered", Tone.DONE, true),
    FAILED(4, "Delivery attempt failed", Tone.EXCEPTION, false),
    RESCHEDULED(4, "Delivery rescheduled", Tone.ATTENTION, false),
    RETURNED(5, "Returned to sender", Tone.EXCEPTION, true),
    CANCELLED(1, "Cancelled", Tone.EXCEPTION, true);

    /** Number of stops on the public transit line. */
    public static final int TOTAL_STOPS = 5;

    /** How the UI colours the current status. */
    public enum Tone { PROGRESS, ATTENTION, DONE, EXCEPTION }

    private final int stop;
    private final String label;
    private final Tone tone;
    private final boolean terminal;

    ParcelStatus(int stop, String label, Tone tone, boolean terminal) {
        this.stop = stop;
        this.label = label;
        this.tone = tone;
        this.terminal = terminal;
    }

    /** Position on the five-stop transit line (1..5). */
    public int stop() {
        return stop;
    }

    public String label() {
        return label;
    }

    public Tone tone() {
        return tone;
    }

    /** No further status change is expected (the live courier position is dropped). */
    public boolean terminal() {
        return terminal;
    }

    /** The status a member of the public may see. */
    public ParcelStatus publicStatus() {
        return this == ASSIGNED ? CREATED : this;
    }

    /**
     * Parses a status token from an event, for example {@code out_for_delivery} from the routing key
     * {@code parcel.status.out_for_delivery} or {@code OUT_FOR_DELIVERY} from a payload field.
     */
    public static Optional<ParcelStatus> fromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalised = token.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (ParcelStatus status : values()) {
            if (status.name().equals(normalised)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
