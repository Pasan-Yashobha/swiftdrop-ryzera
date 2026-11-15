package lk.swiftdrop.parcel.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import lk.swiftdrop.parcel.common.ConflictException;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofDays(7);

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String computeHash(Object payload) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonBytes);
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to calculate request hash", e);
        }
    }

    /**
     * Checks if key exists. Returns existing completed response if replayed with identical payload.
     * Throws ConflictException if currently IN_PROGRESS.
     * Throws UnprocessableException if payload hash differs.
     * Starts new record if not present.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<T> startOrGetReplay(String key, Object payload, Class<T> responseType) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String requestHash = computeHash(payload);
        Instant now = clock.instant();

        Optional<IdempotencyRecord> existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.getStatus() == IdempotencyRecord.Status.IN_PROGRESS) {
                throw new ConflictException(ErrorCode.IDEMPOTENCY_KEY_IN_USE,
                        "A request with Idempotency-Key '" + key + "' is currently being processed");
            }

            if (!record.getRequestHash().equals(requestHash)) {
                throw new UnprocessableException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key '" + key + "' was previously used with a different request payload");
            }

            try {
                return Optional.of(objectMapper.readValue(record.getResponseBody(), responseType));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize cached idempotent response", e);
            }
        }

        IdempotencyRecord newRecord = new IdempotencyRecord(key, requestHash, now, now.plus(TTL));
        repository.save(newRecord);
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompleted(String key, int status, Object responseBody) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(responseBody);
            repository.findByIdempotencyKey(key).ifPresent(record -> {
                record.markCompleted(status, json);
                repository.save(record);
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response body", e);
        }
    }
}
