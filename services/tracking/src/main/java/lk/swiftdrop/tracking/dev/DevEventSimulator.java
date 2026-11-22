package lk.swiftdrop.tracking.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lk.swiftdrop.tracking.common.ApiException;
import lk.swiftdrop.tracking.common.ErrorCode;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.dev.SriLankaCities.City;
import lk.swiftdrop.tracking.domain.TrackingNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Dev profile only. Plays a realistic parcel journey (created, assigned, picked up, in transit with courier
 * positions, out for delivery, delivered or failed) through the real broker, consumer, Redis and push path, so
 * real-time tracking can be demonstrated before the Parcel (M4) and Courier (M7) services exist.
 * Messages go straight to {@code tracking.q} through the default exchange, so other services never see them.
 * The created event deliberately carries a recipient name, phone and street address: the tracking response
 * must not show any of them.
 */
@Component
@Profile("dev")
public class DevEventSimulator {

    private static final Logger log = LoggerFactory.getLogger(DevEventSimulator.class);
    private static final String PRODUCER = "tracking-dev-simulator";

    /** What to simulate; every field is optional. */
    public record SimulateRequest(String trackingNo, String origin, String destination, Integer stepSeconds,
                                  String outcome, String serviceType) {
    }

    /** What was started. */
    public record Simulation(String trackingNo, String parcelId, int events, int stepSeconds, long durationSeconds,
                             String trackingPage, List<String> cities) {
    }

    private record Step(long offsetMillis, Supplier<ObjectNode> event) {
    }

    private final RabbitTemplate rabbitTemplate;
    private final TaskScheduler scheduler;
    private final ObjectMapper objectMapper;
    private final TrackingProperties properties;
    private final Clock clock;

    public DevEventSimulator(RabbitTemplate rabbitTemplate, TaskScheduler scheduler, ObjectMapper objectMapper,
                             TrackingProperties properties, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.scheduler = scheduler;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public Simulation start(SimulateRequest request) {
        City origin = SriLankaCities.find(request.origin()).orElse(SriLankaCities.COLOMBO);
        City destination = SriLankaCities.find(request.destination()).orElse(SriLankaCities.KANDY);
        if (origin.equals(destination)) {
            destination = origin.equals(SriLankaCities.KANDY) ? SriLankaCities.COLOMBO : SriLankaCities.KANDY;
        }
        int step = request.stepSeconds() == null ? 6 : Math.max(2, Math.min(60, request.stepSeconds()));
        boolean fail = "FAILED".equalsIgnoreCase(request.outcome());
        String service = "EXPRESS".equalsIgnoreCase(request.serviceType()) ? "EXPRESS" : "STANDARD";
        String trackingNo = TrackingNumbers.normalize(request.trackingNo()).orElseGet(DevEventSimulator::newTrackingNo);
        String parcelId = UUID.randomUUID().toString();
        String courierId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        AtomicLong sequence = new AtomicLong();
        final City from = origin;
        final City to = destination;

        List<Step> steps = new ArrayList<>();
        long stepMs = step * 1000L;
        long half = stepMs / 2;

        steps.add(new Step(0, () -> {
            ObjectNode data = parcelData(trackingNo, parcelId, sequence);
            data.put("pickupCity", from.name());
            data.put("pickupLat", from.lat());
            data.put("pickupLng", from.lng());
            data.put("dropoffCity", to.name());
            data.put("dropoffLat", to.lat());
            data.put("dropoffLng", to.lng());
            data.put("size", "SMALL");
            data.put("weightKg", 2.0);
            data.put("serviceType", service);
            // Personal data a real producer may include. The Tracking Service must drop all of it.
            data.putObject("recipient").put("name", "Demo Recipient").put("phone", "+94770000000");
            data.putObject("dropoff").put("addressLine", "14 Demo Lane").put("city", to.name());
            return envelope("parcel.created", parcelId, correlationId, data);
        }));
        steps.add(new Step(stepMs, () -> {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("parcelId", parcelId);
            data.put("trackingNo", trackingNo);
            data.put("courierId", courierId);
            return envelope("parcel.assigned", parcelId, correlationId, data);
        }));
        steps.add(new Step(2 * stepMs, () -> status("picked_up", trackingNo, parcelId, courierId, sequence,
                null, correlationId)));
        long inTransit = 3 * stepMs;
        steps.add(new Step(inTransit, () -> status("in_transit", trackingNo, parcelId, courierId, sequence,
                from.name() + " hub", correlationId)));
        for (int k = 1; k <= 6; k++) {
            double fraction = 0.85 * k / 6;
            steps.add(new Step(inTransit + k * half, () -> location(courierId, from, to, fraction, correlationId)));
        }
        long outForDelivery = inTransit + 7 * half;
        steps.add(new Step(outForDelivery, () -> status("out_for_delivery", trackingNo, parcelId, courierId,
                sequence, to.name() + " hub", correlationId)));
        for (int k = 1; k <= 5; k++) {
            double fraction = 0.85 + 0.14 * k / 5;
            steps.add(new Step(outForDelivery + k * half, () -> location(courierId, from, to, fraction,
                    correlationId)));
        }
        long finish = outForDelivery + 6 * half;
        if (fail) {
            steps.add(new Step(finish, () -> {
                ObjectNode envelope = status("failed", trackingNo, parcelId, courierId, sequence, null, correlationId);
                ((ObjectNode) envelope.get("data")).put("failureReason", "NOT_HOME");
                return envelope;
            }));
            steps.add(new Step(finish + stepMs, () -> status("rescheduled", trackingNo, parcelId, null, sequence,
                    null, correlationId)));
        } else {
            steps.add(new Step(finish, () -> status("delivered", trackingNo, parcelId, courierId, sequence, null,
                    correlationId)));
        }

        Instant start = clock.instant();
        for (Step s : steps) {
            scheduler.schedule(() -> send(s.event().get()), start.plusMillis(s.offsetMillis()));
        }
        long duration = steps.get(steps.size() - 1).offsetMillis() / 1000;
        log.info("Simulating parcel {} from {} to {} ({} events over {} s)", trackingNo, from.name(), to.name(),
                steps.size(), duration);
        return new Simulation(trackingNo, parcelId, steps.size(), step, duration, "/track/" + trackingNo,
                SriLankaCities.names());
    }

    /** Publishes any envelope to the tracking queue, for trying out event shapes by hand. */
    public void publishRaw(JsonNode envelope) {
        if (envelope == null || !envelope.isObject() || !envelope.hasNonNull("eventType")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Send an event envelope with at least eventType");
        }
        ObjectNode copy = ((ObjectNode) envelope).deepCopy();
        if (!copy.hasNonNull("eventId")) {
            copy.put("eventId", UUID.randomUUID().toString());
        }
        if (!copy.hasNonNull("occurredAt")) {
            copy.put("occurredAt", clock.instant().toString());
        }
        send(copy);
    }

    private ObjectNode parcelData(String trackingNo, String parcelId, AtomicLong sequence) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("parcelId", parcelId);
        data.put("trackingNo", trackingNo);
        data.put("sequence", sequence.incrementAndGet());
        return data;
    }

    private ObjectNode status(String status, String trackingNo, String parcelId, String courierId, AtomicLong sequence,
                              String hub, String correlationId) {
        ObjectNode data = parcelData(trackingNo, parcelId, sequence);
        data.put("toStatus", status.toUpperCase(Locale.ROOT));
        if (courierId != null) {
            data.put("courierId", courierId);
        }
        if (hub != null) {
            data.put("hub", hub);
        }
        return envelope("parcel.status." + status, parcelId, correlationId, data);
    }

    private ObjectNode location(String courierId, City from, City to, double fraction, String correlationId) {
        // A gentle curve rather than a straight line, so the map looks like a road.
        double lat = from.lat() + (to.lat() - from.lat()) * fraction;
        double lng = from.lng() + (to.lng() - from.lng()) * fraction;
        double bend = Math.sin(Math.PI * fraction) * 0.06;
        ObjectNode data = objectMapper.createObjectNode();
        data.put("courierId", courierId);
        data.put("lat", lat + bend);
        data.put("lng", lng - bend / 2);
        data.put("recordedAt", clock.instant().toString());
        return envelope("courier.location.updated", courierId, correlationId, data);
    }

    private ObjectNode envelope(String eventType, String aggregateId, String correlationId, ObjectNode data) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("version", 1);
        envelope.put("occurredAt", clock.instant().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("correlationId", correlationId);
        envelope.put("aggregateId", aggregateId);
        envelope.set("data", data);
        return envelope;
    }

    private void send(ObjectNode envelope) {
        try {
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            messageProperties.setMessageId(envelope.path("eventId").asText());
            messageProperties.setType(envelope.path("eventType").asText());
            rabbitTemplate.send("", properties.getQueue(),
                    new Message(objectMapper.writeValueAsBytes(envelope), messageProperties));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise simulated event", e);
        } catch (RuntimeException e) {
            log.warn("Simulated event {} not sent: {}", envelope.path("eventType").asText(), e.getMessage());
        }
    }

    private static String newTrackingNo() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format("SD-%04d-%04d", random.nextInt(10_000), random.nextInt(10_000));
    }
}
