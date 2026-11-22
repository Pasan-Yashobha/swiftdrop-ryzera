package lk.swiftdrop.gateway.error;

import io.netty.channel.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Turns every unhandled gateway error (no route, backend down, timeout...) into the standard
 * error format. Runs before Spring Boot's default error handler.
 */
@Component
@Order(-2)
public class ApiErrorWebExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorWebExceptionHandler.class);

    private final ApiErrorWriter writer;

    public ApiErrorWebExceptionHandler(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            String code = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "REQUEST_ERROR";
            return writer.write(exchange, status, code, status.getReasonPhrase());
        }
        if (isCause(ex, ConnectException.class, ConnectTimeoutException.class, UnknownHostException.class)) {
            log.warn("Backend unreachable for {}: {}", exchange.getRequest().getPath(), ex.getMessage());
            return writer.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "The service is not available right now. Please try again shortly.");
        }
        if (isCause(ex, TimeoutException.class)) {
            return writer.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT",
                    "The service took too long to answer.");
        }
        log.error("Unexpected gateway error for {}", exchange.getRequest().getPath(), ex);
        return writer.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Please try again later.");
    }

    @SafeVarargs
    private static boolean isCause(Throwable ex, Class<? extends Throwable>... types) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
