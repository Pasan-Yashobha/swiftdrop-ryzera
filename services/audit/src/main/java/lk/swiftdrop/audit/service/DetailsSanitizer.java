package lk.swiftdrop.audit.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The audit log keeps IDs and short facts, never secrets or personal data. Values are stored as text
 * (so they come back from the database exactly as they went in, which the hash chain relies on).
 */
@Component
public class DetailsSanitizer {

    static final int MAX_ENTRIES = 30;
    static final int MAX_VALUE_LENGTH = 200;

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|secret|token|authorization|email|phone|address|hash|credential).*|(?i)(api)?key");

    public Map<String, String> sanitize(Map<String, Object> data) {
        Map<String, String> clean = new TreeMap<>();
        if (data == null) {
            return clean;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (clean.size() >= MAX_ENTRIES) {
                break;
            }
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null || SENSITIVE_KEY.matcher(key).matches()) {
                continue;
            }
            clean.put(key, text(value));
        }
        return clean;
    }

    private static String text(Object value) {
        if (value instanceof Map<?, ?>) {
            return "[object]";
        }
        if (value instanceof Collection<?> || value.getClass().isArray()) {
            return "[list]";
        }
        String text = String.valueOf(value);
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) : text;
    }
}
