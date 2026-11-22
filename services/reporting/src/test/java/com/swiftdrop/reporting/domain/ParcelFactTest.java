package com.swiftdrop.reporting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelFactTest {

    @Test
    @DisplayName("ParcelFact properties and initial state")
    void testParcelFactProperties() {
        ParcelFact fact = new ParcelFact();
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        fact.setParcelId(parcelId);
        fact.setTrackingNo("TRK-12345");
        fact.setCourierId(courierId);
        fact.setCurrentStatus("CREATED");
        fact.setLastEventAt(now);
        fact.setFee(new BigDecimal("500.00"));

        assertThat(fact.getParcelId()).isEqualTo(parcelId);
        assertThat(fact.getTrackingNo()).isEqualTo("TRK-12345");
        assertThat(fact.getCourierId()).isEqualTo(courierId);
        assertThat(fact.getCurrentStatus()).isEqualTo("CREATED");
        assertThat(fact.getLastEventAt()).isEqualTo(now);
        assertThat(fact.getFee()).isEqualTo(new BigDecimal("500.00"));
    }
}
