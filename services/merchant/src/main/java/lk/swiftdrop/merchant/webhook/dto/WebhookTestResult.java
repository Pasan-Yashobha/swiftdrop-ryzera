package lk.swiftdrop.merchant.webhook.dto;

/**
 * Outcome of the signed {@code webhook.test} ping.
 *
 * @param delivered  the endpoint answered with a 2xx status
 * @param httpStatus status the endpoint answered with, null when there was no answer
 * @param durationMs time until the answer or the failure
 * @param error      short, safe reason when not delivered ("timeout", "connection failed", ...); never exception text
 */
public record WebhookTestResult(boolean delivered, Integer httpStatus, long durationMs, String error) {
}
