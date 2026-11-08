package lk.swiftdrop.dispatch.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftdrop")
public record AppProperties(
        Dispatch dispatch,
        Integration integration,
        Security security) {

    public record Dispatch(
            int maxActivePerCourier,
            int zoneCacheTtlMinutes,
            int idempotencyTtlHours) {
        public Dispatch {
            if (maxActivePerCourier <= 0) {
                maxActivePerCourier = 8;
            }
            if (zoneCacheTtlMinutes <= 0) {
                zoneCacheTtlMinutes = 10;
            }
            if (idempotencyTtlHours <= 0) {
                idempotencyTtlHours = 24;
            }
        }
    }

    public record Integration(
            String parcelServiceUrl,
            String pricingServiceUrl) {
    }

    public record Security(
            String jwksUri,
            String devHmacSecret,
            List<String> corsAllowedOrigins) {
    }
}
