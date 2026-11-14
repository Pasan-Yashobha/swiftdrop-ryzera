package lk.swiftdrop.gateway.web;

import com.fasterxml.jackson.databind.JsonNode;
import lk.swiftdrop.gateway.config.GatewayProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Feeds the "System Status" admin page: asks every service for /actuator/health in parallel
 * (2 s timeout each) and caches the answer for 10 s so many open browser tabs cost almost nothing.
 * Only ADMIN users can call it (rule for /api/v1/admin/** in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/system")
public class SystemHealthController {

    public record ServiceHealth(String service, String status, long responseMs) {
    }

    private final WebClient client;
    private final Mono<List<ServiceHealth>> cached;

    public SystemHealthController(WebClient.Builder builder, GatewayProperties props) {
        this.client = builder.build();
        this.cached = Flux.fromIterable(props.uris().entrySet())
                .flatMap(entry -> check(entry.getKey(), entry.getValue()))
                .sort(Comparator.comparing(ServiceHealth::service))
                .collectList()
                .cache(Duration.ofSeconds(10));
    }

    @GetMapping("/health")
    public Mono<List<ServiceHealth>> health() {
        return cached;
    }

    private Mono<ServiceHealth> check(String name, String baseUri) {
        long start = System.nanoTime();
        return client.get().uri(baseUri + "/actuator/health")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(2))
                .map(json -> json.path("status").asText("UNKNOWN"))
                .onErrorReturn("DOWN")
                .map(status -> new ServiceHealth(name, status, (System.nanoTime() - start) / 1_000_000));
    }
}
