package lk.swiftdrop.tracking.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code swiftdrop.tracking}. Defaults match application.yml so tests can run without it. */
@ConfigurationProperties(prefix = "swiftdrop.tracking")
public class TrackingProperties {

    /** False in tests: the Redis store, broadcaster and subscriber are replaced by in-memory doubles. */
    private boolean redisEnabled = true;
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of("http://localhost:4200"));

    private String exchange = "swiftdrop.events";
    private String deadLetterExchange = "swiftdrop.dlx";
    private String queue = "tracking.q";
    private String deadLetterQueue = "tracking.q.dlq";
    private List<String> bindingKeys = new ArrayList<>(List.of("parcel.#", "courier.location.#"));

    private String keyPrefix = "tracking:";
    private String updatesChannel = "tracking:updates";
    private Duration snapshotTtl = Duration.ofDays(30);
    private Duration processedEventTtl = Duration.ofDays(7);
    private int timelineLimit = 50;
    private int trailLimit = 30;
    private List<ParcelStatus> shareLocationStatuses =
            new ArrayList<>(List.of(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY));
    private Duration locationStaleAfter = Duration.ofMinutes(15);

    private final Sse sse = new Sse();
    private final Eta eta = new Eta();

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public List<String> getBindingKeys() {
        return bindingKeys;
    }

    public void setBindingKeys(List<String> bindingKeys) {
        this.bindingKeys = bindingKeys;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getUpdatesChannel() {
        return updatesChannel;
    }

    public void setUpdatesChannel(String updatesChannel) {
        this.updatesChannel = updatesChannel;
    }

    public Duration getSnapshotTtl() {
        return snapshotTtl;
    }

    public void setSnapshotTtl(Duration snapshotTtl) {
        this.snapshotTtl = snapshotTtl;
    }

    public Duration getProcessedEventTtl() {
        return processedEventTtl;
    }

    public void setProcessedEventTtl(Duration processedEventTtl) {
        this.processedEventTtl = processedEventTtl;
    }

    public int getTimelineLimit() {
        return timelineLimit;
    }

    public void setTimelineLimit(int timelineLimit) {
        this.timelineLimit = timelineLimit;
    }

    public int getTrailLimit() {
        return trailLimit;
    }

    public void setTrailLimit(int trailLimit) {
        this.trailLimit = trailLimit;
    }

    public List<ParcelStatus> getShareLocationStatuses() {
        return shareLocationStatuses;
    }

    public void setShareLocationStatuses(List<ParcelStatus> shareLocationStatuses) {
        this.shareLocationStatuses = shareLocationStatuses;
    }

    public Duration getLocationStaleAfter() {
        return locationStaleAfter;
    }

    public void setLocationStaleAfter(Duration locationStaleAfter) {
        this.locationStaleAfter = locationStaleAfter;
    }

    public Sse getSse() {
        return sse;
    }

    public Eta getEta() {
        return eta;
    }

    /** Server-sent event stream limits. */
    public static class Sse {

        private Duration timeout = Duration.ofMinutes(30);
        private long heartbeatIntervalMs = 20_000;
        private long reconnectDelayMs = 3_000;
        private int maxConnections = 5_000;
        private int maxConnectionsPerParcel = 50;

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public long getHeartbeatIntervalMs() {
            return heartbeatIntervalMs;
        }

        public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
        }

        public long getReconnectDelayMs() {
            return reconnectDelayMs;
        }

        public void setReconnectDelayMs(long reconnectDelayMs) {
            this.reconnectDelayMs = reconnectDelayMs;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getMaxConnectionsPerParcel() {
            return maxConnectionsPerParcel;
        }

        public void setMaxConnectionsPerParcel(int maxConnectionsPerParcel) {
            this.maxConnectionsPerParcel = maxConnectionsPerParcel;
        }
    }

    /** Inputs of the ETA estimate. */
    public static class Eta {

        private String zone = "Asia/Colombo";
        private double averageSpeedKmh = 25;
        private double roadFactor = 1.3;
        private int handlingMinutes = 5;
        private double expressFactor = 0.5;
        private int deliveryDayStartHour = 8;
        private int deliveryDayEndHour = 20;

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        public double getAverageSpeedKmh() {
            return averageSpeedKmh;
        }

        public void setAverageSpeedKmh(double averageSpeedKmh) {
            this.averageSpeedKmh = averageSpeedKmh;
        }

        public double getRoadFactor() {
            return roadFactor;
        }

        public void setRoadFactor(double roadFactor) {
            this.roadFactor = roadFactor;
        }

        public int getHandlingMinutes() {
            return handlingMinutes;
        }

        public void setHandlingMinutes(int handlingMinutes) {
            this.handlingMinutes = handlingMinutes;
        }

        public double getExpressFactor() {
            return expressFactor;
        }

        public void setExpressFactor(double expressFactor) {
            this.expressFactor = expressFactor;
        }

        public int getDeliveryDayStartHour() {
            return deliveryDayStartHour;
        }

        public void setDeliveryDayStartHour(int deliveryDayStartHour) {
            this.deliveryDayStartHour = deliveryDayStartHour;
        }

        public int getDeliveryDayEndHour() {
            return deliveryDayEndHour;
        }

        public void setDeliveryDayEndHour(int deliveryDayEndHour) {
            this.deliveryDayEndHour = deliveryDayEndHour;
        }
    }
}
