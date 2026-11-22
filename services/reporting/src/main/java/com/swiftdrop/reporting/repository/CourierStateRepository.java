package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.CourierState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CourierStateRepository extends JpaRepository<CourierState, UUID> {

    /** Count of couriers currently marked as available (used in summary endpoint). */
    @Query("SELECT COUNT(c) FROM CourierState c WHERE c.available = true")
    long countAvailable();
}
