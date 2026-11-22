package lk.swiftdrop.audit.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lk.swiftdrop.audit.domain.AuditEntry;
import lk.swiftdrop.audit.domain.AuditLog;
import lk.swiftdrop.audit.domain.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Writes one audit record: skip duplicates, chain it to the previous record, insert. */
@Service
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditLogRepository repository;
    private final JdbcTemplate jdbc;
    private final Counter recorded;
    private final Counter duplicates;

    public AuditRecorder(AuditLogRepository repository, JdbcTemplate jdbc, MeterRegistry meters) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.recorded = Counter.builder("audit_events_recorded_total").tag("result", "recorded").register(meters);
        this.duplicates = Counter.builder("audit_events_recorded_total").tag("result", "duplicate").register(meters);
    }

    /** @return true if a new record was written, false if this event was already recorded */
    @Transactional
    public boolean record(AuditEntry entry) {
        // One writer at a time (readers are not blocked), so "previous record" is well defined even with several instances.
        jdbc.execute("LOCK TABLE audit_log IN SHARE ROW EXCLUSIVE MODE");

        if (repository.existsByEventId(entry.eventId())) {
            duplicates.increment();
            log.debug("Duplicate event {} ignored", entry.eventId());
            return false;
        }
        String prevHash = repository.findTopByOrderBySeqDesc().map(AuditLog::getRowHash).orElse(HashChain.GENESIS);
        String rowHash = HashChain.hash(prevHash, entry);
        repository.save(new AuditLog(entry, Instant.now().truncatedTo(ChronoUnit.MICROS), prevHash, rowHash));
        recorded.increment();
        log.info("Audit recorded {} (event {})", entry.action(), entry.eventId());
        return true;
    }
}
