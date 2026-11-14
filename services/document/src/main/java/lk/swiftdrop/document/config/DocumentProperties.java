package lk.swiftdrop.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings of the Document service that are not Spring's own; see application.yml for the environment variables. */
@ConfigurationProperties("document")
public record DocumentProperties(@DefaultValue Messaging messaging, @DefaultValue Security security,
                                 @DefaultValue Downstream downstream) {

    /**
     * RabbitMQ. With {@code enabled=false} nothing connects to the broker: no consumer, no relay. The outbox table is
     * still written, so events are published later once messaging is switched on.
     */
    public record Messaging(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("swiftdrop.events") String exchange,
            @DefaultValue("swiftdrop.dlx") String deadLetterExchange,
            @DefaultValue("document.receipts.q") String queue,
            @DefaultValue("document.receipts.dlq") String deadLetterQueue,
            @DefaultValue("parcel.status.delivered") String deliveredRoutingKey,
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("1000") long retryInitialIntervalMs,
            @DefaultValue("2.0") double retryMultiplier,
            @DefaultValue("10") int prefetch,
            @DefaultValue("1000") long outboxPollIntervalMs,
            @DefaultValue("50") int outboxBatchSize,
            @DefaultValue("5000") long confirmTimeoutMs) { }

    /** The shared secret other services send in {@code X-Internal-Token}. Blank means no internal call is accepted. */
    public record Security(String internalToken) { }

    /** Where to ask for parcel details when the delivered event does not carry everything a receipt needs. */
    public record Downstream(String parcelUrl,
                             @DefaultValue("/internal/parcels/{parcelId}") String parcelPath) { }
}
