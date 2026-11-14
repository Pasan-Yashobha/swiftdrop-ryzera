package lk.swiftdrop.tracking.dev;

import com.fasterxml.jackson.databind.JsonNode;
import lk.swiftdrop.tracking.dev.DevEventSimulator.SimulateRequest;
import lk.swiftdrop.tracking.dev.DevEventSimulator.Simulation;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev profile only (this bean does not exist in any other profile, and security opens /api/v1/dev/** only in dev).
 * <pre>
 * POST /api/v1/dev/tracking/simulate  {"origin":"Colombo","destination":"Kandy","stepSeconds":6,"outcome":"DELIVERED"}
 * POST /api/v1/dev/tracking/events    any event envelope, published to tracking.q as is
 * </pre>
 */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/dev/tracking")
public class DevTrackingController {

    private final DevEventSimulator simulator;

    public DevTrackingController(DevEventSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping(path = "/simulate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Simulation> simulate(@RequestBody(required = false) SimulateRequest request) {
        SimulateRequest effective = request != null ? request : new SimulateRequest(null, null, null, null, null, null);
        return ResponseEntity.accepted().body(simulator.start(effective));
    }

    @PostMapping(path = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> publish(@RequestBody JsonNode envelope) {
        simulator.publishRaw(envelope);
        return ResponseEntity.accepted().build();
    }
}
