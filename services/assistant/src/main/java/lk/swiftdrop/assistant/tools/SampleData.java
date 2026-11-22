package lk.swiftdrop.assistant.tools;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Stand-in answers used only while the Tracking or Parcel service is not connected (blank URL).
 * Every answer says {@code demo: true} so the assistant can tell the user it is sample data.
 */
@Component
public class SampleData {
    static final String NOTE = "Sample data: this service is not connected yet, so this is not a real parcel.";
    private final Clock clock;

    public SampleData(Clock clock) { this.clock = clock; }

    public Map<String, Object> tracking(String trackingNumber) {
        var now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demo", true);
        result.put("note", NOTE);
        result.put("trackingNumber", trackingNumber);
        result.put("status", "OUT_FOR_DELIVERY");
        result.put("events", List.of(
                event("CREATED", now.minus(30, ChronoUnit.HOURS).toString(), "Parcel booked"),
                event("PICKED_UP", now.minus(26, ChronoUnit.HOURS).toString(), "Collected from the sender"),
                event("IN_TRANSIT", now.minus(10, ChronoUnit.HOURS).toString(), "Left the sorting hub"),
                event("OUT_FOR_DELIVERY", now.minus(2, ChronoUnit.HOURS).toString(), "With the courier for delivery")));
        return result;
    }

    public Map<String, Object> history(int limit) {
        var now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        var all = List.of(
                parcel("SD-4821-0937", "OUT_FOR_DELIVERY", now.minus(30, ChronoUnit.HOURS).toString()),
                parcel("SD-4790-1122", "DELIVERED", now.minus(9, ChronoUnit.DAYS).toString()),
                parcel("SD-4755-3380", "DELIVERED", now.minus(21, ChronoUnit.DAYS).toString()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demo", true);
        result.put("note", NOTE);
        result.put("parcels", all.subList(0, Math.min(limit, all.size())));
        return result;
    }

    private static Map<String, Object> event(String status, String at, String description) {
        return Map.of("status", status, "at", at, "description", description);
    }

    private static Map<String, Object> parcel(String trackingNumber, String status, String createdAt) {
        return Map.of("trackingNumber", trackingNumber, "status", status, "createdAt", createdAt);
    }
}
