package lk.swiftdrop.dispatch.repository;

import java.util.List;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50BySentAtIsNullOrderByCreatedAtAsc();
}
