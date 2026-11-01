package lk.swiftdrop.merchant.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private TransactionTemplate transaction;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void enqueuedEventIsStoredWithTheCallersTransaction() throws Exception {
        MDC.put("correlationId", "corr-42");

        transaction.executeWithoutResult(status ->
                outboxService.enqueue("merchant.approved", "agg-1", Map.of("merchantId", "agg-1", "decidedBy", "admin")));

        assertThat(repository.findAll()).hasSize(1);
        OutboxEvent row = repository.findAll().get(0);
        assertThat(row.getEventType()).isEqualTo("merchant.approved");
        assertThat(row.getAggregateId()).isEqualTo("agg-1");
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getAttempts()).isZero();

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(UUID.fromString(envelope.get("eventId").asText())).hasToString(row.getEventId());
        assertThat(envelope.get("eventType").asText()).isEqualTo("merchant.approved");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(Instant.parse(envelope.get("occurredAt").asText())).isNotNull();
        assertThat(envelope.get("producer").asText()).isEqualTo("merchant-service");
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-42");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo("agg-1");
        assertThat(envelope.get("data").get("decidedBy").asText()).isEqualTo("admin");
    }

    @Test
    void eventIsDiscardedWhenTheCallersTransactionRollsBack() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            outboxService.enqueue("merchant.approved", "agg-1", Map.of("merchantId", "agg-1"));
            throw new IllegalStateException("business failure after the event was queued");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
    }

    @Test
    void enqueueWithoutATransactionIsRefused() {
        assertThatThrownBy(() -> outboxService.enqueue("merchant.approved", "agg-1", Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(repository.count()).isZero();
    }

    @Test
    void correlationIdIsGeneratedWhenTheRequestHasNone() throws Exception {
        transaction.executeWithoutResult(status -> outboxService.enqueue("merchant.registered", "agg-1", Map.of()));

        JsonNode envelope = objectMapper.readTree(repository.findAll().get(0).getPayload());
        assertThat(UUID.fromString(envelope.get("correlationId").asText())).isNotNull();
    }

    @Test
    void oversizedEventsAreRefusedWithoutEchoingTheirContent() {
        Map<String, Object> huge = Map.of("blob", "secret-content-".repeat(400));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                outboxService.enqueue("merchant.registered", "agg-1", huge)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("merchant.registered")
                .hasMessageNotContaining("secret-content");
    }

    @Test
    void dataThatCannotBeSerialisedIsRefused() {
        Map<String, Object> unserialisable = Map.of("thing", new Object());

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                outboxService.enqueue("merchant.registered", "agg-1", unserialisable)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be serialised");
    }
}
