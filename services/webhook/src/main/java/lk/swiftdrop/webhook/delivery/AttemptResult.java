package lk.swiftdrop.webhook.delivery;

import java.util.Map;

/**
 * What happened when a delivery was attempted.
 *
 * @param kind            how the attempt is classified
 * @param httpStatus      the receiver's status; null when no response arrived
 * @param durationMs      time from sending to the response (0 when nothing was sent)
 * @param error           short, fixed description of a failure; never contains URLs or response content
 * @param responseSnippet the first bytes of the response body (at most 512 characters)
 * @param requestHeaders  headers that were sent, signature shortened; null when nothing was sent
 */
public record AttemptResult(Kind kind, Integer httpStatus, long durationMs, String error, String responseSnippet,
                            Map<String, String> requestHeaders) {

    public enum Kind {
        /** 2xx. */
        SUCCESS,
        /** Worth retrying: timeout, connection error, 5xx, 429, 408, Merchant Service unavailable. */
        TEMPORARY,
        /** Retrying cannot help: other 4xx, redirect, no usable webhook configuration. */
        PERMANENT
    }

    /** A failure before anything was sent. */
    static AttemptResult notSent(Kind kind, String error) {
        return new AttemptResult(kind, null, 0, error, null, null);
    }

    AttemptResult withRequestHeaders(Map<String, String> headers) {
        return new AttemptResult(kind, httpStatus, durationMs, error, responseSnippet, headers);
    }
}
