package lk.swiftdrop.gateway.web;

import lk.swiftdrop.gateway.error.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

/** Answer sent when a backend service is down or its circuit breaker is open. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<ApiError> fallback(@PathVariable String service, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        ApiError body = new ApiError(Instant.now(), status.value(), status.name(), "SERVICE_UNAVAILABLE",
                "The " + service + " service is not available right now. Please try again shortly.",
                exchange.getRequest().getPath().value(), CorrelationIdWebFilter.idOf(exchange));
        return ResponseEntity.status(status).body(body);
    }
}
