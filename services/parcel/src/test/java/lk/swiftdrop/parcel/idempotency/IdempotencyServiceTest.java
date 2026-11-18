package lk.swiftdrop.parcel.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import lk.swiftdrop.parcel.common.ConflictException;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.UnprocessableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository repository;

    private ObjectMapper objectMapper;
    private Clock clock;
    private IdempotencyService idempotencyService;

    private static final Instant NOW = Instant.parse("2026-11-15T07:00:00Z");

    record DummyPayload(String name, int value) {}
    record DummyResponse(String id, String status) {}

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        idempotencyService = new IdempotencyService(repository, objectMapper, clock);
    }

    @Test
    @DisplayName("New key creates an IN_PROGRESS idempotency record")
    void testNewKeyStartsInProgress() {
        String key = "idem-key-100";
        DummyPayload payload = new DummyPayload("Test", 42);

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        Optional<DummyResponse> result = idempotencyService.startOrGetReplay(key, payload, DummyResponse.class);

        assertThat(result).isEmpty();

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());

        IdempotencyRecord saved = captor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo(key);
        assertThat(saved.getStatus()).isEqualTo(IdempotencyRecord.Status.IN_PROGRESS);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Concurrent request with key already IN_PROGRESS throws ConflictException")
    void testInProgressKeyThrowsConflict() {
        String key = "idem-key-200";
        DummyPayload payload = new DummyPayload("Test", 42);
        String hash = idempotencyService.computeHash(payload);

        IdempotencyRecord existing = new IdempotencyRecord(key, hash, NOW, NOW.plusSeconds(3600));
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.startOrGetReplay(key, payload, DummyResponse.class))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_IN_USE);
    }

    @Test
    @DisplayName("Replaying completed key with identical payload returns cached response")
    void testCompletedKeyReplayReturnsCached() throws Exception {
        String key = "idem-key-300";
        DummyPayload payload = new DummyPayload("Test", 42);
        String hash = idempotencyService.computeHash(payload);

        DummyResponse expectedResponse = new DummyResponse("RES-1", "SUCCESS");
        String responseJson = objectMapper.writeValueAsString(expectedResponse);

        IdempotencyRecord existing = new IdempotencyRecord(key, hash, NOW, NOW.plusSeconds(3600));
        existing.markCompleted(200, responseJson);

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        Optional<DummyResponse> result = idempotencyService.startOrGetReplay(key, payload, DummyResponse.class);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("Replaying completed key with different payload throws UnprocessableException")
    void testReusedKeyWithDifferentPayloadThrowsUnprocessable() {
        String key = "idem-key-400";
        DummyPayload originalPayload = new DummyPayload("Original", 1);
        DummyPayload newPayload = new DummyPayload("Different", 2);

        String originalHash = idempotencyService.computeHash(originalPayload);

        IdempotencyRecord existing = new IdempotencyRecord(key, originalHash, NOW, NOW.plusSeconds(3600));
        existing.markCompleted(200, "{}");

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.startOrGetReplay(key, newPayload, DummyResponse.class))
                .isInstanceOf(UnprocessableException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    @DisplayName("recordCompleted marks existing record as COMPLETED with response body")
    void testRecordCompletedUpdatesRecord() {
        String key = "idem-key-500";
        IdempotencyRecord record = new IdempotencyRecord(key, "hash", NOW, NOW.plusSeconds(3600));

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        DummyResponse response = new DummyResponse("PCL-123", "CREATED");
        idempotencyService.recordCompleted(key, 201, response);

        assertThat(record.getStatus()).isEqualTo(IdempotencyRecord.Status.COMPLETED);
        assertThat(record.getResponseStatus()).isEqualTo(201);
        assertThat(record.getResponseBody()).contains("PCL-123");
        verify(repository).save(record);
    }

    @Test
    @DisplayName("startOrCheckReplay with new key returns NEW and saves record")
    void testStartOrCheckReplayNewKey() {
        String key = "check-key-1";
        DummyPayload payload = new DummyPayload("Check", 10);
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        IdempotencyService.ReplayStatus status = idempotencyService.startOrCheckReplay(key, payload);
        assertThat(status).isEqualTo(IdempotencyService.ReplayStatus.NEW);

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("startOrCheckReplay with completed key and identical payload returns REPLAY")
    void testStartOrCheckReplayCompleted() {
        String key = "check-key-2";
        DummyPayload payload = new DummyPayload("Check", 10);
        String hash = idempotencyService.computeHash(payload);

        IdempotencyRecord record = new IdempotencyRecord(key, hash, NOW, NOW.plusSeconds(3600));
        record.markCompleted(204, "");
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        IdempotencyService.ReplayStatus status = idempotencyService.startOrCheckReplay(key, payload);
        assertThat(status).isEqualTo(IdempotencyService.ReplayStatus.REPLAY);
    }

    @Test
    @DisplayName("startOrCheckReplay with in-progress key throws ConflictException")
    void testStartOrCheckReplayInProgressThrowsConflict() {
        String key = "check-key-3";
        DummyPayload payload = new DummyPayload("Check", 10);
        String hash = idempotencyService.computeHash(payload);

        IdempotencyRecord record = new IdempotencyRecord(key, hash, NOW, NOW.plusSeconds(3600));
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> idempotencyService.startOrCheckReplay(key, payload))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_IN_USE);
    }

    @Test
    @DisplayName("startOrCheckReplay with completed key and different payload throws UnprocessableException")
    void testStartOrCheckReplayDifferentPayloadThrowsUnprocessable() {
        String key = "check-key-4";
        DummyPayload originalPayload = new DummyPayload("Check", 10);
        DummyPayload differentPayload = new DummyPayload("Check", 99);
        String hash = idempotencyService.computeHash(originalPayload);

        IdempotencyRecord record = new IdempotencyRecord(key, hash, NOW, NOW.plusSeconds(3600));
        record.markCompleted(204, "");
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> idempotencyService.startOrCheckReplay(key, differentPayload))
                .isInstanceOf(UnprocessableException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    @DisplayName("startOrCheckReplay with null or blank key returns NEW without saving")
    void testStartOrCheckReplayBlankKey() {
        IdempotencyService.ReplayStatus status = idempotencyService.startOrCheckReplay("  ", new DummyPayload("A", 1));
        assertThat(status).isEqualTo(IdempotencyService.ReplayStatus.NEW);
    }

    @Test
    @DisplayName("recordCompleted with null response body saves empty string")
    void testRecordCompletedNullBody() {
        String key = "idem-key-null-body";
        IdempotencyRecord record = new IdempotencyRecord(key, "hash", NOW, NOW.plusSeconds(3600));
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        idempotencyService.recordCompleted(key, 204, null);

        assertThat(record.getStatus()).isEqualTo(IdempotencyRecord.Status.COMPLETED);
        assertThat(record.getResponseStatus()).isEqualTo(204);
        assertThat(record.getResponseBody()).isEqualTo("");
    }
}
