package lk.swiftdrop.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.swiftdrop.gateway.web.CorrelationIdWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Writes the standard error body. Never includes stack traces or personal data. */
@Component
public class ApiErrorWriter {

    private final ObjectMapper mapper;

    public ApiErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ApiError build(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        return new ApiError(Instant.now(), status.value(), status.name(), code, message,
                exchange.getRequest().getPath().value(), CorrelationIdWebFilter.idOf(exchange));
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(build(exchange, status, code, message));
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
