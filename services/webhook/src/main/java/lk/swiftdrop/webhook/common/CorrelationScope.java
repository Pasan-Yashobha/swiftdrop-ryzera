package lk.swiftdrop.webhook.common;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Puts a correlation id into the logging MDC for work that does not run inside an HTTP request. */
public final class CorrelationScope {

    /** Same rule as {@link CorrelationIdFilter}: ids end up in logs and headers, so only short, harmless values. */
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private CorrelationScope() {
    }

    /** The given id when it is acceptable, otherwise a fresh one. */
    public static String sanitise(String candidate) {
        return candidate != null && ACCEPTED.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }

    /** Runs {@code work} with {@code correlationId} in the MDC and restores the previous value afterwards. */
    public static <T> T call(String correlationId, Supplier<T> work) {
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previous);
            }
        }
    }

    public static void run(String correlationId, Runnable work) {
        call(correlationId, () -> {
            work.run();
            return null;
        });
    }
}
