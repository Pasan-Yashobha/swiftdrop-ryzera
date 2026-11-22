package lk.swiftdrop.tracking.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Small helpers to read optional values from event JSON by dotted path ("pickup.city"). */
final class Json {

    private Json() {
    }

    /** The node at a dotted path, or null. */
    static JsonNode at(JsonNode root, String path) {
        if (root == null || path == null) {
            return null;
        }
        JsonNode node = root;
        for (String part : path.split("\\.")) {
            if (node == null || !node.isObject()) {
                return null;
            }
            node = node.get(part);
        }
        return node == null || node.isNull() || node.isMissingNode() ? null : node;
    }

    /** First non-blank text (or number as text) found at any of the paths. */
    static String text(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = at(root, path);
            if (node != null && (node.isTextual() || node.isNumber())) {
                String value = node.asText().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    /** First number (or numeric text) found at any of the paths. */
    static Double number(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = at(root, path);
            if (node == null) {
                continue;
            }
            if (node.isNumber()) {
                return node.asDouble();
            }
            if (node.isTextual()) {
                try {
                    return Double.parseDouble(node.asText().trim());
                } catch (NumberFormatException ignored) {
                    // try the next path
                }
            }
        }
        return null;
    }

    static Long whole(JsonNode root, String... paths) {
        Double value = number(root, paths);
        if (value == null || value.isNaN() || value.isInfinite() || value < 0) {
            return null;
        }
        return value.longValue();
    }

    static BigDecimal decimal(JsonNode root, String... paths) {
        Double value = number(root, paths);
        return value == null || value.isNaN() || value.isInfinite() ? null : BigDecimal.valueOf(value);
    }

    /** ISO-8601 instant, offset date-time, or local date-time taken as UTC. */
    static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
