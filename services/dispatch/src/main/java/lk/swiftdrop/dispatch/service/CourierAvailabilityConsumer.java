package lk.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.dispatch.config.RabbitConfig;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import lk.swiftdrop.dispatch.repository.CourierWorkloadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes courier.availability.changed events from Courier Service to update local workload table.
 */
@Service
public class CourierAvailabilityConsumer {

    private static final Logger log = LoggerFactory.getLogger(CourierAvailabilityConsumer.class);

    private final CourierWorkloadRepository workloadRepository;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;

    public CourierAvailabilityConsumer(CourierWorkloadRepository workloadRepository,
                                     SseEmitterService sseEmitterService,
                                     ObjectMapper objectMapper) {
        this.workloadRepository = workloadRepository;
        this.sseEmitterService = sseEmitterService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.COURIER_QUEUE)
    @Transactional
    public void handleCourierAvailabilityChanged(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            // Handle both flat event and envelope structure {data: {...}}
            JsonNode data = root.has("data") ? root.get("data") : root;

            if (!data.has("courierId")) {
                log.warn("Ignoring courier availability event without courierId: {}", payload);
                return;
            }

            UUID courierId = UUID.fromString(data.get("courierId").asText());
            boolean available = data.has("available") ? data.get("available").asBoolean(true) : true;
            int activeDeliveries = data.has("activeDeliveries") ? data.get("activeDeliveries").asInt(0) : 0;
            UUID zoneId = data.has("zoneId") && !data.get("zoneId").isNull()
                    ? UUID.fromString(data.get("zoneId").asText())
                    : null;

            CourierWorkload workload = workloadRepository.findByIdForUpdate(courierId)
                    .orElseGet(() -> new CourierWorkload(courierId, zoneId, 0, 8, available, null));

            if (zoneId != null) {
                workload.setZoneId(zoneId);
            }

            // Specification requirement: an event older than the last assignment to that courier never lowers the count
            Instant eventTime = null;
            if (root.has("occurredAt")) {
                try {
                    eventTime = Instant.parse(root.get("occurredAt").asText());
                } catch (Exception ignored) {
                }
            } else if (data.has("occurredAt")) {
                try {
                    eventTime = Instant.parse(data.get("occurredAt").asText());
                } catch (Exception ignored) {
                }
            }

            if (workload.getLastAssignedAt() != null && eventTime != null && eventTime.isBefore(workload.getLastAssignedAt())) {
                if (activeDeliveries < workload.getActiveDeliveries()) {
                    log.info("Ignoring lower activeDeliveries from stale event (occurredAt {} is before lastAssignedAt {})",
                            eventTime, workload.getLastAssignedAt());
                    activeDeliveries = workload.getActiveDeliveries();
                }
            }

            workload.updateStatus(available, activeDeliveries);
            CourierWorkload saved = workloadRepository.save(workload);

            log.info("Updated courier workload from event: courierId={}, available={}, activeDeliveries={}",
                    courierId, available, activeDeliveries);

            // Broadcast real-time change to all open Dispatcher Consoles
            sseEmitterService.broadcast("workload.changed", CourierWorkloadResponse.from(saved));

        } catch (Exception ex) {
            log.error("Failed to process courier availability message: {}", ex.getMessage(), ex);
        }
    }
}

