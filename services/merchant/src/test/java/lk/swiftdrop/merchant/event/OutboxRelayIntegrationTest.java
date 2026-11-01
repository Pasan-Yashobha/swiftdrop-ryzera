package lk.swiftdrop.merchant.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.net.ConnectException;
import java.time.Clock;
import java.util.Map;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

/** The relay against the real outbox table, with the broker replaced by a mock. */
class OutboxRelayIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private Clock clock;

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final AppProperties properties = TestProperties.defaults();

    @BeforeEach
    void queueTwoEvents() {
        transaction.executeWithoutResult(status -> {
            outboxService.enqueue("merchant.registered", "m-1", Map.of("merchantId", "m-1"));
            outboxService.enqueue("merchant.approved", "m-1", Map.of("merchantId", "m-1"));
        });
    }

    @Test
    void publishedRowsAreMarkedInTheDatabase() {
        doAnswer(invocation -> {
            ((CorrelationData) invocation.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThat(new OutboxRelay(repository, rabbitTemplate, properties, clock).publishBatch()).isEqualTo(2);

        assertThat(repository.findAll()).allSatisfy(row -> {
            assertThat(row.getPublishedAt()).isNotNull();
            assertThat(row.getAttempts()).isEqualTo(1);
        });
        assertThat(repository.findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(Pageable.unpaged()))
                .isEmpty();
    }

    @Test
    void failedPublishLeavesRowsPendingWithAttemptCounted() {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThat(new OutboxRelay(repository, rabbitTemplate, properties, clock).publishBatch()).isZero();

        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findAll()).filteredOn(row -> row.getPublishedAt() == null).hasSize(2);
        assertThat(repository.findAll()).filteredOn(row -> row.getAttempts() == 1).hasSize(1);
        assertThat(repository.findAll()).filteredOn(row -> row.getLastError() != null)
                .singleElement().satisfies(row -> assertThat(row.getLastError()).doesNotContain("merchantId"));
    }
}
