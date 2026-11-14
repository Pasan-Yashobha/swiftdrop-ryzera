package lk.swiftdrop.assistant.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import lk.swiftdrop.assistant.config.AssistantProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Allows each user a fixed number of chat messages per minute, so one account cannot use up the model's quota. */
@Component
public class MessageRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final int limit;
    private final Map<String, Deque<Instant>> sent = new HashMap<>();

    public MessageRateLimiter(Clock clock, AssistantProperties properties) {
        this.clock = clock;
        this.limit = properties.chat().messagesPerMinute();
    }

    public synchronized void check(String userId) {
        Instant now = clock.instant();
        sent.values().removeIf(times -> times.isEmpty() || times.peekLast().isBefore(now.minus(WINDOW)));
        Deque<Instant> times = sent.computeIfAbsent(userId, key -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) times.removeFirst();
        if (times.size() >= limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You are sending messages too quickly. Please wait a moment and try again");
        }
        times.addLast(now);
    }
}
