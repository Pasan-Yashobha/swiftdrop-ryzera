package com.swiftdrop.reporting.event;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Utility for robustly extracting typed values from generic JSON event payloads.
 */
public final class EventPayloadHelper {

    private EventPayloadHelper() {}

    public static UUID getUuid(Map<String, Object> payload, String key) {
        if (payload == null) return null;
        Object val = payload.get(key);
        if (val == null) return null;
        if (val instanceof UUID u) return u;
        try {
            return UUID.fromString(val.toString().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String getString(Map<String, Object> payload, String key) {
        if (payload == null) return null;
        Object val = payload.get(key);
        return val != null ? val.toString() : null;
    }

    public static BigDecimal getBigDecimal(Map<String, Object> payload, String key) {
        if (payload == null) return BigDecimal.ZERO;
        Object val = payload.get(key);
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public static boolean getBoolean(Map<String, Object> payload, String key) {
        if (payload == null) return false;
        Object val = payload.get(key);
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString().trim());
    }

    public static int getInt(Map<String, Object> payload, String key) {
        if (payload == null) return 0;
        Object val = payload.get(key);
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
