package lk.swiftdrop.audit.service;

import jakarta.persistence.criteria.Predicate;
import lk.swiftdrop.audit.common.NotFoundException;
import lk.swiftdrop.audit.domain.AuditLog;
import lk.swiftdrop.audit.domain.AuditLogRepository;
import lk.swiftdrop.audit.web.dto.AuditLogResponse;
import lk.swiftdrop.audit.web.dto.PageResponse;
import lk.swiftdrop.audit.web.dto.VerifyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read side: search, single record, and chain verification. Nothing here can change a record. */
@Service
public class AuditQueryService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH = 500;

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Filters are all optional and combined with AND. Newest first. */
    public record Filter(String actor, String action, String entityType, String entityId, Instant from, Instant to) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(Filter filter, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "seq")));
        Page<AuditLog> result = repository.findAll(specification(filter), pageable);
        return new PageResponse<>(result.getContent().stream().map(AuditLogResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(UUID id) {
        return repository.findById(id).map(AuditLogResponse::from)
                .orElseThrow(() -> new NotFoundException("Audit record not found"));
    }

    /** Re-computes every hash from the first record on and reports the first record that does not match. */
    @Transactional(readOnly = true)
    public VerifyResponse verify() {
        String expectedPrev = HashChain.GENESIS;
        long checked = 0;
        int pageNumber = 0;
        Page<AuditLog> batch;
        do {
            batch = repository.findAll(PageRequest.of(pageNumber++, VERIFY_BATCH, Sort.by("seq")));
            for (AuditLog row : batch.getContent()) {
                checked++;
                if (!expectedPrev.equals(row.getPrevHash())) {
                    return broken(checked, row, "The link to the previous record is broken");
                }
                String recomputed = HashChain.hash(row.getPrevHash(), row.getEventId(), row.getOccurredAt(),
                        row.getActorId(), row.getActorRole(), row.getAction(), row.getEntityType(),
                        row.getEntityId(), row.getCorrelationId(), row.getDetails());
                if (!recomputed.equals(row.getRowHash())) {
                    return broken(checked, row, "The record's content does not match its hash");
                }
                expectedPrev = row.getRowHash();
            }
        } while (batch.hasNext());
        return new VerifyResponse(true, checked, null, null, Instant.now());
    }

    private static VerifyResponse broken(long checked, AuditLog row, String reason) {
        return new VerifyResponse(false, checked, row.getSeq(), reason, Instant.now());
    }

    private static Specification<AuditLog> specification(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.actor() != null && !f.actor().isBlank()) {
                predicates.add(cb.equal(root.get("actorId"), f.actor().trim()));
            }
            if (f.action() != null && !f.action().isBlank()) {
                predicates.add(cb.equal(root.get("action"), f.action().trim().toUpperCase()));
            }
            if (f.entityType() != null && !f.entityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), f.entityType().trim().toUpperCase()));
            }
            if (f.entityId() != null && !f.entityId().isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), f.entityId().trim()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), f.to()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
