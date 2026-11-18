package lk.swiftdrop.parcel.parcel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    List<Parcel> findByStatusOrderByCreatedAtAsc(ParcelStatus status);

    Page<Parcel> findByCustomerId(String customerId, Pageable pageable);

    Page<Parcel> findByCustomerIdAndStatus(String customerId, ParcelStatus status, Pageable pageable);

    @Query("SELECT p FROM Parcel p WHERE p.customerId = :customerId " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (LOWER(p.trackingNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(p.recipientName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Parcel> findByCustomerFiltered(
            @Param("customerId") String customerId,
            @Param("status") ParcelStatus status,
            @Param("search") String search,
            Pageable pageable);
}
