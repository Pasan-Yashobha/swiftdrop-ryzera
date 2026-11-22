package lk.swiftdrop.webhook.delivery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.swiftdrop.webhook.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * When the next automatic attempt happens: attempt 2 after 5 s, 3 after 30 s, 4 after 2 min, 5 after 10 min and 6
 * after 30 min (configurable). Each delay is a delay queue whose TTL is that delay; there is no attempt 7.
 */
@Component
public class RetrySchedule {

    /** A retry delay and the queue that implements it. */
    public record Delay(String queue, Duration duration) {
    }

    private final List<Delay> delays;

    public RetrySchedule(AppProperties properties) {
        List<Long> millis = properties.delivery().retryDelaysMs();
        List<Delay> list = new ArrayList<>();
        for (int i = 0; i < AppProperties.Delivery.DELAY_QUEUES.size(); i++) {
            list.add(new Delay(AppProperties.Delivery.DELAY_QUEUES.get(i), Duration.ofMillis(millis.get(i))));
        }
        this.delays = List.copyOf(list);
    }

    /** Attempts in total, including the first one (6). */
    public int maxAttempts() {
        return delays.size() + 1;
    }

    /** @return the delay before the next attempt, or empty when {@code failedAttemptNo} was the last one */
    public Optional<Delay> after(int failedAttemptNo) {
        if (failedAttemptNo < 1 || failedAttemptNo > delays.size()) {
            return Optional.empty();
        }
        return Optional.of(delays.get(failedAttemptNo - 1));
    }
}
