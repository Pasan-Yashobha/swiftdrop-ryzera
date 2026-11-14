package lk.swiftdrop.audit;

import lk.swiftdrop.audit.domain.AuditLog;
import lk.swiftdrop.audit.domain.AuditLogRepository;
import lk.swiftdrop.audit.service.AuditQueryService;
import lk.swiftdrop.audit.web.dto.VerifyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL and RabbitMQ (Testcontainers, needs Docker). Publishes events the way Merchant does and checks the
 * whole path: queue, idempotency, hash chain, dead-letter queue, append-only database and the admin API.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuditIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AuditLogRepository repository;
    @Autowired AuditQueryService query;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    // ------------------------------------------------------------------ consuming events

    @Test
    void publishedEventIsRecordedWithoutSensitiveData() {
        UUID id = UUID.randomUUID();
        publish("merchant.approved", event(id, "merchant.approved", "m-100",
                "{\"merchantId\":\"m-100\",\"decidedBy\":\"admin-9\",\"contactEmail\":\"x@y.z\"}"));
        awaitRecorded(id);

        AuditLog row = repository.findByEventId(id).orElseThrow();
        assertThat(row.getAction()).isEqualTo("MERCHANT_APPROVED");
        assertThat(row.getEntityType()).isEqualTo("MERCHANT");
        assertThat(row.getEntityId()).isEqualTo("m-100");
        assertThat(row.getActorId()).isEqualTo("admin-9");
        assertThat(row.getCorrelationId()).isEqualTo("corr-1");
        assertThat(row.getDetails()).containsEntry("merchantId", "m-100").doesNotContainKey("contactEmail");
        assertThat(row.getRowHash()).hasSize(64);
    }

    @Test
    void sameEventDeliveredTwiceIsRecordedOnce() {
        UUID duplicate = UUID.randomUUID();
        UUID marker = UUID.randomUUID();
        String json = event(duplicate, "user.locked", "u-1", "{\"userId\":\"u-1\"}");
        publish("user.locked", json);
        publish("user.locked", json);
        publish("user.registered", event(marker, "user.registered", "u-2", "{\"userId\":\"u-2\"}"));
        awaitRecorded(marker); // one consumer thread, in order: when the marker is there, both copies were handled

        assertThat(repository.findAll().stream().filter(r -> r.getEventId().equals(duplicate))).hasSize(1);
    }

    @Test
    void hashChainVerifies() {
        awaitRecorded(publishNew("merchant.registered"));
        awaitRecorded(publishNew("merchant.rejected"));

        VerifyResponse result = query.verify();

        assertThat(result.valid()).isTrue();
        assertThat(result.checked()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void malformedMessageEndsInTheDeadLetterQueue() {
        Message bad = MessageBuilder.withBody("this is not json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        rabbitTemplate.send("swiftdrop.events", "merchant.registered", bad);

        Message dead = rabbitTemplate.receive("audit.dlq", 15_000);

        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("this is not json");
    }

    // ------------------------------------------------------------------ append-only and tamper evidence

    @Test
    void auditLogCannotBeChangedOrEmptied() {
        awaitRecorded(publishNew("user.role_changed"));

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'X'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_log")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void tamperingWithARecordIsDetected() {
        awaitRecorded(publishNew("merchant.registered"));
        awaitRecorded(publishNew("merchant.approved"));
        Long seq = jdbc.queryForObject("SELECT min(seq) FROM audit_log", Long.class);
        String original = jdbc.queryForObject("SELECT action FROM audit_log WHERE seq = ?", String.class, seq);

        jdbc.execute("ALTER TABLE audit_log DISABLE TRIGGER USER"); // what a database administrator could do
        try {
            jdbc.update("UPDATE audit_log SET action = 'TAMPERED' WHERE seq = ?", seq);

            VerifyResponse result = query.verify();

            assertThat(result.valid()).isFalse();
            assertThat(result.firstBrokenSeq()).isEqualTo(seq);
        } finally {
            jdbc.update("UPDATE audit_log SET action = ? WHERE seq = ?", original, seq);
            jdbc.execute("ALTER TABLE audit_log ENABLE TRIGGER USER");
        }
        assertThat(query.verify().valid()).isTrue();
    }

    // ------------------------------------------------------------------ admin API

    @Test
    void adminCanSearchAndFilterTheAuditLog() throws Exception {
        UUID id = publishNew("merchant.approved");
        awaitRecorded(id);

        mvc.perform(get("/api/v1/admin/audit-logs").param("action", "merchant_approved").param("size", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("MERCHANT_APPROVED"))
                .andExpect(jsonPath("$.size").value(5));

        String recordId = repository.findByEventId(id).orElseThrow().getId().toString();
        mvc.perform(get("/api/v1/admin/audit-logs/" + recordId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(id.toString()));
    }

    @Test
    void adminCanVerifyTheChain() throws Exception {
        awaitRecorded(publishNew("user.registered"));

        mvc.perform(get("/api/v1/admin/audit-logs/verify")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void unknownRecordGives404() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsMayReadTheAuditLog() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/audit-logs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit-logs")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private UUID publishNew(String type) {
        UUID id = UUID.randomUUID();
        publish(type, event(id, type, "agg-" + id, "{\"actorId\":\"admin-1\"}"));
        return id;
    }

    private void awaitRecorded(UUID eventId) {
        await().atMost(Duration.ofSeconds(20)).until(() -> repository.existsByEventId(eventId));
    }

    private void publish(String routingKey, String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        rabbitTemplate.send("swiftdrop.events", routingKey, message);
    }

    private static String event(UUID id, String type, String aggregateId, String dataJson) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"" + type + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-11-10T08:30:00Z\",\"producer\":\"merchant-service\","
                + "\"correlationId\":\"corr-1\",\"aggregateId\":\"" + aggregateId + "\",\"data\":" + dataJson + "}";
    }
}
