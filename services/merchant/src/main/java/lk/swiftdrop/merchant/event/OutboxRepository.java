package lk.swiftdrop.merchant.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Oldest unpublished events first, so that events of one aggregate leave in the order they happened. */
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(Pageable pageable);

    /** @return the number of deleted events */
    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
