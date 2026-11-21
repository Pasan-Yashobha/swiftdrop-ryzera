package lk.swiftdrop.courier.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftdrop")
public record AppProperties(
        Courier courier,
        Integration integration,
        Security security,
        Storage storage,
        Messaging messaging) {

    public record Courier(
            int defaultMaxActive,
            int gpsPrecisionDecimals) {
        public Courier {
            if (defaultMaxActive <= 0) {
                defaultMaxActive = 8;
            }
            if (gpsPrecisionDecimals <= 0) {
                gpsPrecisionDecimals = 4;
            }
        }
    }

    public record Integration(
            String parcelServiceUrl,
            String identityServiceUrl,
            String internalToken,
            Boolean allowMockFallback) {
    }

    public record Security(
            String jwksUri,
            String devHmacSecret,
            List<String> corsAllowedOrigins,
            String jwtIssuer) {
        @ConstructorBinding
        public Security {
            if (jwtIssuer == null || jwtIssuer.isBlank()) jwtIssuer = "swiftdrop-identity";
        }

        public Security(String jwksUri, String devHmacSecret, List<String> corsAllowedOrigins) {
            this(jwksUri, devHmacSecret, corsAllowedOrigins, "swiftdrop-identity");
        }
    }

    public record Storage(
            String type,
            String localDir,
            String s3Endpoint,
            String s3Bucket,
            String s3Region,
            String s3AccessKey,
            String s3SecretKey) {
        @ConstructorBinding
        public Storage {}

        // Preserve existing local/test callers.
        public Storage(String type, String localDir, String endpoint, String bucket, String region) {
            this(type, localDir, endpoint, bucket, region, null, null);
        }

        @Override
        public String toString() {
            return "Storage[type=" + type + ", credentials=REDACTED]";
        }
    }

    public record Messaging(
            String exchange,
            String deadLetterExchange,
            String queue,
            String deadLetterQueue,
            String assignedRoutingKey,
            String reassignedRoutingKey) {
        public Messaging {
            if (exchange == null || exchange.isBlank()) exchange = "swiftdrop.events";
            if (deadLetterExchange == null || deadLetterExchange.isBlank()) deadLetterExchange = "swiftdrop.dlx";
            if (queue == null || queue.isBlank()) queue = "courier.assignments.q";
            if (deadLetterQueue == null || deadLetterQueue.isBlank()) deadLetterQueue = "courier.assignments.dlq";
            if (assignedRoutingKey == null || assignedRoutingKey.isBlank()) assignedRoutingKey = "parcel.assigned";
            if (reassignedRoutingKey == null || reassignedRoutingKey.isBlank()) reassignedRoutingKey = "parcel.reassigned";
        }
    }
}
