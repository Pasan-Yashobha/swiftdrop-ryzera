package lk.swiftdrop.gateway.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * First filter of every request: keeps the caller's X-Correlation-Id or creates one, passes it
 * to the backend, returns it in the response, and writes one access-log line per request
 * (method, path, status, time, correlation id; never headers, tokens or request bodies).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String ATTRIBUTE = "swiftdrop.correlationId";
    private static final Logger log = LoggerFactory.getLogger("access");

    public static String idOf(ServerWebExchange exchange) {
        Object id = exchange.getAttribute(ATTRIBUTE);
        return id == null ? null : id.toString();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = (supplied == null || supplied.isBlank() || supplied.length() > 100)
                ? UUID.randomUUID().toString() : supplied;
        exchange.getAttributes().put(ATTRIBUTE, id);
        exchange.getResponse().getHeaders().set(HEADER, id);

        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, id).build();
        long start = System.nanoTime();
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> log.info("{} {} -> {} in {} ms [{}]",
                        request.getMethod(), request.getPath().value(),
                        exchange.getResponse().getStatusCode() == null ? "-" : exchange.getResponse().getStatusCode().value(),
                        (System.nanoTime() - start) / 1_000_000, id));
    }
}
