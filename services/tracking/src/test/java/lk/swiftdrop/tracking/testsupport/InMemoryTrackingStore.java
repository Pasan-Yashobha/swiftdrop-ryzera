package lk.swiftdrop.tracking.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.store.TrackingStore;

/**
 * Test double of the Redis store. Parcels are kept as JSON strings, exactly like Redis, so every read returns a
 * fresh copy and the stored text can be inspected (for the privacy tests).
 */
public class InMemoryTrackingStore implements TrackingStore {

    private final ObjectMapper objectMapper;
    private final Map<String, String> parcels = new ConcurrentHashMap<>();
    private final Map<String, String> parcelIds = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> couriers = new ConcurrentHashMap<>();
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    public InMemoryTrackingStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ParcelTracking> find(String trackingNo) {
        return Optional.ofNullable(parcels.get(trackingNo)).map(this::read);
    }

    @Override
    public synchronized Optional<ParcelTracking> compute(
            String trackingNo, Function<Optional<ParcelTracking>, Optional<ParcelTracking>> change) {
        Optional<ParcelTracking> next = change.apply(find(trackingNo));
        next.ifPresent(parcel -> parcels.put(trackingNo, write(parcel)));
        return next.map(parcel -> read(write(parcel)));
    }

    @Override
    public void indexParcelId(String parcelId, String trackingNo) {
        parcelIds.put(parcelId, trackingNo);
    }

    @Override
    public Optional<String> findTrackingNo(String parcelId) {
        return Optional.ofNullable(parcelIds.get(parcelId));
    }

    @Override
    public void linkCourier(String courierId, String trackingNo) {
        couriers.computeIfAbsent(courierId, key -> ConcurrentHashMap.newKeySet()).add(trackingNo);
    }

    @Override
    public void unlinkCourier(String courierId, String trackingNo) {
        Set<String> set = couriers.get(courierId);
        if (set != null) {
            set.remove(trackingNo);
        }
    }

    @Override
    public Set<String> parcelsOfCourier(String courierId) {
        return new HashSet<>(couriers.getOrDefault(courierId, Set.of()));
    }

    @Override
    public boolean markProcessed(String eventId) {
        return processed.add(eventId);
    }

    @Override
    public void unmarkProcessed(String eventId) {
        processed.remove(eventId);
    }

    /** The stored JSON text of a parcel (what Redis would hold). */
    public String rawJson(String trackingNo) {
        return parcels.get(trackingNo);
    }

    public boolean isProcessed(String eventId) {
        return processed.contains(eventId);
    }

    public void put(ParcelTracking parcel) {
        parcels.put(parcel.getTrackingNo(), write(parcel));
    }

    public void clear() {
        parcels.clear();
        parcelIds.clear();
        couriers.clear();
        processed.clear();
    }

    private ParcelTracking read(String json) {
        try {
            return objectMapper.readValue(json, ParcelTracking.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(ParcelTracking parcel) {
        try {
            return objectMapper.writeValueAsString(parcel);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
