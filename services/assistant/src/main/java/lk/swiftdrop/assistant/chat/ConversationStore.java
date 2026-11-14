package lk.swiftdrop.assistant.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.assistant.config.AssistantProperties;
import lk.swiftdrop.assistant.llm.LlmMessage;
import org.springframework.stereotype.Component;

/**
 * Remembers each user's recent conversations in memory only (there is no database for this service yet).
 * Conversations belong to the user who started them: the key includes the user id, so guessing someone else's
 * conversation id gets a new, empty conversation. They expire after a period of inactivity, each user keeps a few,
 * and only the most recent question-and-answer turns are kept.
 */
@Component
public class ConversationStore {
    static final int MAX_PER_USER = 5;
    static final int MAX_TOTAL = 2000;

    private final Clock clock;
    private final Duration ttl;
    private final int maxTurns;
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();

    public ConversationStore(Clock clock, AssistantProperties properties) {
        this.clock = clock;
        this.ttl = Duration.ofMinutes(properties.chat().conversationTtlMinutes());
        this.maxTurns = properties.chat().maxHistoryTurns();
    }

    /** The id to use: the given one if this user already has it, otherwise a fresh one. */
    public synchronized String resolve(String userId, String requestedId) {
        evictExpired();
        if (requestedId != null && conversations.containsKey(key(userId, requestedId))) {
            return requestedId;
        }
        String id = UUID.randomUUID().toString();
        makeRoom(userId);
        conversations.put(key(userId, id), new Conversation(userId, clock.instant()));
        return id;
    }

    /** The remembered messages, oldest first, as one flat list. */
    public synchronized List<LlmMessage> history(String userId, String id) {
        Conversation conversation = conversations.get(key(userId, id));
        if (conversation == null) return List.of();
        List<LlmMessage> flat = new ArrayList<>();
        conversation.turns.forEach(flat::addAll);
        return flat;
    }

    /** Stores one finished question-and-answer turn and drops the oldest turns beyond the limit. */
    public synchronized void append(String userId, String id, List<LlmMessage> turn) {
        Conversation conversation = conversations.get(key(userId, id));
        if (conversation == null) return;
        conversation.turns.addLast(List.copyOf(turn));
        while (conversation.turns.size() > maxTurns) conversation.turns.removeFirst();
        conversation.lastUsed = clock.instant();
    }

    public synchronized void delete(String userId, String id) {
        conversations.remove(key(userId, id));
    }

    private void evictExpired() {
        Instant cutoff = clock.instant().minus(ttl);
        conversations.values().removeIf(conversation -> conversation.lastUsed.isBefore(cutoff));
    }

    private void makeRoom(String userId) {
        long own = conversations.values().stream().filter(conversation -> conversation.userId.equals(userId)).count();
        for (Iterator<Conversation> it = conversations.values().iterator(); own >= MAX_PER_USER && it.hasNext(); ) {
            if (it.next().userId.equals(userId)) {
                it.remove();
                own--;
            }
        }
        while (conversations.size() >= MAX_TOTAL) {
            conversations.remove(conversations.keySet().iterator().next());
        }
    }

    private static String key(String userId, String id) { return userId + "|" + id; }

    private static final class Conversation {
        final String userId;
        final Deque<List<LlmMessage>> turns = new ArrayDeque<>();
        Instant lastUsed;

        Conversation(String userId, Instant created) {
            this.userId = userId;
            this.lastUsed = created;
        }
    }
}
