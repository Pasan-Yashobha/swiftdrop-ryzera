package lk.swiftdrop.parcel.parcel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {

    List<ParcelStatusHistory> findByParcelIdOrderBySequenceNumberAsc(UUID parcelId);

    @Query("SELECT MAX(h.sequenceNumber) FROM ParcelStatusHistory h WHERE h.parcelId = :parcelId")
    Optional<Integer> findMaxSequenceNumber(@Param("parcelId") UUID parcelId);
}
