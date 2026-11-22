package lk.swiftdrop.parcel.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository repository;

    private ObjectMapper objectMapper;
    private Clock clock;
    private OutboxService outboxService;

    private static final Instant NOW = Instant.parse("2026-11-15T07:15:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        outboxService = new OutboxService(repository, objectMapper, clock);
    }

    @Test
    @DisplayName("recordEvent formats valid JSON payload matching SwiftDrop event envelope")
    void testRecordEventEnvelopeFormat() throws Exception {
        String eventType = "parcel.created";
        String aggregateId = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parcelId", aggregateId);
        data.put("trackingNo", "SD260001ABC");
        data.put("customerId", "cust-100");

        outboxService.recordEvent(eventType, aggregateId, data);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(eventType);
        assertThat(saved.getAggregateType()).isEqualTo("Parcel");
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getPublishedAt()).isNull();

        JsonNode json = objectMapper.readTree(saved.getPayload());
        assertThat(json.path("eventType").asText()).isEqualTo(eventType);
        assertThat(json.path("producer").asText()).isEqualTo("parcel-service");
        assertThat(json.path("version").asInt()).isEqualTo(1);
        assertThat(json.path("aggregateId").asText()).isEqualTo(aggregateId);
        assertThat(json.path("data").path("trackingNo").asText()).isEqualTo("SD260001ABC");
        assertThat(json.path("data").path("customerId").asText()).isEqualTo("cust-100");
    }
}
